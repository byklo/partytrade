package com.partytrade;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;


public class Backend {
  public static void main(String[] args) throws java.io.IOException {
    ActorSystem system = ActorSystem.create();
    ActorRef bfx = system.actorOf(BFXWebSocket.props(), "bfx-websocket");
    ActorRef kernel = system.actorOf(Kernel.props(bfx), "kernel");
    Endpoint end = new Endpoint(kernel);
    end.run(system);
  }
}