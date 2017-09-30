package com.partytrade;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.math.BigDecimal;

import com.partytrade.model.PriceUpdate;


public class Kernel extends AbstractActor {
	BigDecimal currentPrice = new BigDecimal(0.0);

  static Props props() {
  	return Props.create(Kernel.class, () -> new Kernel());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
    	.match(PriceUpdate.class, update -> {
    		currentPrice = update.price;
    	})
    	.build();
  }
}