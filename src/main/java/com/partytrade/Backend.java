package com.partytrade;

import akka.actor.ActorSystem;


public class Backend {
  public static void main(String[] args) throws java.io.IOException {
    ActorSystem system = ActorSystem.create();
    system.actorOf(BFXWebSocket.props(), "bfx-websocket");
    system.actorOf(Kernel.props(), "kernel");
  }
}