from RLTest import Env
from sqlalchemy import create_engine
from sqlalchemy.sql import text
import time
import subprocess
import docker
import signal

class TimeLimit(object):
    """
    A context manager that fires a TimeExpired exception if it does not
    return within the specified amount of time.
    """

    def __init__(self, timeout, env, msg):
        self.timeout = timeout
        self.env = env
        self.msg = msg

    def __enter__(self):
        signal.signal(signal.SIGALRM, self.handler)
        signal.setitimer(signal.ITIMER_REAL, self.timeout, 0)

    def __exit__(self, exc_type, exc_value, traceback):
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, signal.SIG_DFL)

    def handler(self, signum, frame):
        self.env.assertTrue(False, message=self.msg)
        raise Exception(self.msg)

def Connect():
    ConnectionStr = 'oracle://{user}:{password}@{db}'.format(user='system', password='oracle', db='localhost:1521/xe')
    engine = create_engine(ConnectionStr).execution_options(autocommit=True)
    conn = engine.connect()
    return conn

def GetConnection():
    while True:
        try:
            return Connect() 
        except Exception as e:
            time.sleep(1)

def GetDockerContainer():
    client = docker.from_env()
    container = [container for container in client.containers.list() if container.attrs['Config']['Image'] == 'quay.io/maksymbilenko/oracle-12c']
    if len(container) == 0:
        print('Starting oracle container')
        process = subprocess.Popen(['/bin/bash', '../install_oracle.sh'], stdout=subprocess.PIPE)
        while len(container) == 0:
            container = [container for container in client.containers.list() if container.attrs['Config']['Image'] == 'quay.io/maksymbilenko/oracle-12c']
    else:
        print('Oracle container already running')
    
    return container[0]

class genericTest:
    def __init__(self, writePolicy):
        
        self.container = GetDockerContainer()

        self.dbConn = GetConnection()

        self.env = Env(module='../bin/RedisGears/redisgears.so', moduleArgs='CreateVenv 1 pythonInstallationDir ../../bin/RedisGears/ PluginsDirectory ../../bin/RedisGears_JVMPlugin/plugin/ JvmOptions -Djava.class.path=../../bin/RedisGears_JVMPlugin/gears_runtime/target/gear_runtime-jar-with-dependencies.jar JvmPath ../../bin/RedisGears_JVMPlugin/bin/OpenJDK/jdk-11.0.9.1+1/')
        with open('../target/rghibernate-jar-with-dependencies.jar', 'rb') as f:
            self.env.cmd('RG.JEXECUTE', 'com.redislabs.WriteBehind', f.read())

        with open('../src/test/resources/hibernate.cfg.xml', 'rt') as f:
            self.env.cmd('RG.TRIGGER', 'SYNC.REGISTERCONNECTOR', 'oracle_connector', '10', '10', '5', f.read())

        with open('../src/test/resources/Student.hbm.xml', 'rt') as f:
            self.env.cmd('RG.TRIGGER', 'SYNC.REGISTERSOURCE', 'students_src', 'oracle_connector', writePolicy, f.read())

    def setUp(self):
        try:
            self.dbConn.execute(text('delete from student'))
        except Exception:
            pass
        self.env.cmd('flushall')

class testWriteBehind(genericTest):
    def __init__(self):
        genericTest.__init__(self, 'WriteBehind')

    def testSimpleWriteBehind(self):
        self.env.cmd('hset', 'Student:1', 'firstName', 'foo', 'lastName', 'bar', 'email', 'email')

        result = None
        res = None
        with TimeLimit(10, self.env, 'Failed waiting for data to reach the db'):
            while result is None or res is None:
                time.sleep(0.1)
                try:
                    result = self.dbConn.execute(text('select * from student'))
                    res = result.next()
                except Exception as e:
                    pass

        self.env.assertEqual(res, ('1', 'foo', 'bar', 'email'))

        self.env.cmd('del', 'Student:1')

        with TimeLimit(10, self.env, 'Failed waiting for data to delete from db'):
            while res is not None:
                time.sleep(0.1)
                result = self.dbConn.execute(text('select * from student'))
                res = None
                try:
                    res = result.next()
                except Exception:
                    pass

    def testSimpleWriteBehind2(self):
        for i in range(100):
            self.env.cmd('hmset', 'Student:%d' % i, 'firstName', 'foo', 'lastName', 'bar', 'email', 'email')

        result = None
        res = None
        with TimeLimit(10, self.env, 'Failed waiting for data to reach the db'):
            while result is None or res is None or res[0] != 100:
                time.sleep(0.1)
                try:
                    result = self.dbConn.execute(text('select count(*) from student'))
                    res = result.next()
                except Exception as e:
                    pass

        self.env.assertEqual(res, (100,))

        for i in range(100):
            self.env.cmd('del', 'Student:%d' % i)
        

        with TimeLimit(10, self.env, 'Failed waiting for data to delete from db'):
            while res is not None:
                time.sleep(0.1)
                result = self.dbConn.execute(text('select * from student'))
                res = None
                try:
                    res = result.next()
                except Exception:
                    pass

    def testStopDBOnTrafic(self):
        self.container.stop()

        for i in range(100):
            self.env.cmd('hmset', 'Student:%d' % i, 'firstName', 'foo', 'lastName', 'bar', 'email', 'email')

        with TimeLimit(60*5, self.env, 'Failed connecting to db after docker restart'):
            self.container = GetDockerContainer()
            self.dbConn = GetConnection()

        # make sure all data was written
        result = None
        res = None
        with TimeLimit(10, self.env, 'Failed waiting for data to reach the db'):
            while result is None or res is None or res[0] != 100:
                time.sleep(0.1)
                try:
                    result = self.dbConn.execute(text('select count(*) from student'))
                    res = result.next()
                except Exception as e:
                    pass
        self.env.assertEqual(res, (100,))
