set -x

redis-server --loadmodule ./bin/RedisGears/redisgears.so CreateVenv 1 pythonInstallationDir ./bin/RedisGears/ \
	PluginsDirectory ./bin/RedisGears_JVMPlugin/plugin/ \
	JvmOptions "-Djava.class.path=./bin/RedisGears_JVMPlugin/gears_runtime/target/gear_runtime-jar-with-dependencies.jar" \
	JvmPath ./bin/RedisGears_JVMPlugin/bin/OpenJDK/jdk-11.0.9.1+1/ &

 redis-cli ping
 while [  $? != 0 ]; do
 	sleep 1
 	redis-cli ping
 done

redis-cli -x RG.JEXECUTE com.redislabs.WriteBehind < ./target/rghibernate-jar-with-dependencies.jar

wait < <(jobs -p)