package com.redislabs;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import gears.ExecutionMode;
import gears.GearsBuilder;
import gears.GearsFuture;
import gears.readers.CommandReader;
import gears.readers.KeysReader;
import gears.readers.StreamReader;
import gears.readers.StreamReader.FailurePolicy;
import oracle.ucp.common.waitfreepool.Tuple;

public class WriteBehind{
  
  public static void main(String[] args) throws Exception {
    
    // add connector registration
    CommandReader newConnectorReader = new CommandReader().setTrigger("SYNC.REGISTERCONNECTOR");
    GearsBuilder.CreateGearsBuilder(newConnectorReader, "Register a new connector").
    map(r->{
      String connectorName = new String((byte[])r[1]);
      String connectorXml = new String((byte[])r[5]);
      int batchSize = Integer.parseInt(new String((byte[])r[2]));
      int duration = Integer.parseInt(new String((byte[])r[3]));
      int retryInterval = Integer.parseInt(new String((byte[])r[4]));
      if(Connector.GetConnector(connectorName)!=null) {
        throw new Exception("connector already exists");
      }
      new Connector(connectorName, connectorXml, batchSize, duration, retryInterval);
      return "OK";
    }).register(ExecutionMode.SYNC);
    
    // add source registration
    CommandReader newSourceReader = new CommandReader().setTrigger("SYNC.REGISTERSOURCE");
    GearsBuilder.CreateGearsBuilder(newSourceReader, "Registe a new source").
    map(r->{
      String sourceName = new String((byte[])r[1]);
      String connectorName = new String((byte[])r[2]);
      String writePolicy = new String((byte[])r[3]);
      boolean writeThrough;
      if(writePolicy.equals("WriteThrough")) {
        writeThrough = true;
      }else if(writePolicy.equals("WriteBehind")) {
        writeThrough = false;
      }else {
        throw new Exception("Write policy should be either WriteThrough or WriteBehind");
      }
      String sourceXml = new String((byte[])r[4]);
      if(Source.getSource(sourceName) != null) {
        throw new Exception("source already exists");
      }
      Connector c = Connector.GetConnector(connectorName);
      if(c == null) {
        throw new Exception("connector does not exists");
      }
      Source s = new Source(sourceName, connectorName, sourceXml, writeThrough);
      c.addSource(s);
      return "OK";
    }).register(ExecutionMode.SYNC);
    
    // remove source
    CommandReader newRemoveSourceReader = new CommandReader().setTrigger("SYNC.UNREGISTERSOURCE");
    GearsBuilder.CreateGearsBuilder(newRemoveSourceReader, "Unregiste source").
    map(r->{
      String sourceName = new String((byte[])r[1]);
      Source s = Source.getSource(sourceName);
      if(s == null) {
        throw new Exception("source does exists");
      }
      s.unregister();
      return "OK";
    }).register(ExecutionMode.SYNC);
    
    // remove connector
    CommandReader newRemoveConnectorReader = new CommandReader().setTrigger("SYNC.UNREGISTERCONNECTOR");
    GearsBuilder.CreateGearsBuilder(newRemoveConnectorReader, "Unregiste connector").
    map(r->{
      String connectorName = new String((byte[])r[1]);
      Connector c = Connector.GetConnector(connectorName);
      if(c == null) {
        throw new Exception("connector does exists");
      }
      c.unregister();
      return "OK";
    }).register(ExecutionMode.SYNC);
    
    // dump connectors
    CommandReader dumpConnectorsReader = new CommandReader().setTrigger("SYNC.DUMPCONNECTORS");
    GearsBuilder.CreateGearsBuilder(dumpConnectorsReader, "Dump connectors").
    flatMap(r->{
      return Connector.getAllConnectors();
    }).register(ExecutionMode.SYNC);
    
    // dump sources
    CommandReader dumpSourcesReader = new CommandReader().setTrigger("SYNC.DUMPSOURCES");
    GearsBuilder.CreateGearsBuilder(dumpSourcesReader, "Dump sources").
    flatMap(r->{
      return Source.getAllSources();
    }).register(ExecutionMode.SYNC);
    
    // general information
    CommandReader syncInfoReader = new CommandReader().setTrigger("SYNC.INFO");
    GearsBuilder.CreateGearsBuilder(syncInfoReader, "General info about sync").
    flatMap(r->{
      String subInfoCommand = null;
      
      if(r.length > 1) {
        subInfoCommand = new String((byte[])r[1]);
      }
      
      if("PendingIds".equals(subInfoCommand)) {
        List<String> res = new ArrayList<>();
        for(Tuple<String, GearsFuture<Serializable>> t : Source.queue) {
          res.add(t.get1());
        }
        return res;
      }
      
      if(subInfoCommand != null) {
        throw new Exception("Subinfo command not exists");
      }
      
      LinkedList<String> res = new LinkedList<>();
      res.push("PendingFutureObjects");
      res.push(Integer.toString(Source.queue.size()));
      return res;
      
    }).register(ExecutionMode.SYNC);

  }
}
