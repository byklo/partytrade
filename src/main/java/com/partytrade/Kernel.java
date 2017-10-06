package com.partytrade;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.math.BigDecimal;

import com.partytrade.model.PriceUpdate;
import com.partytrade.model.Order;
import com.partytrade.BFXWebSocket.Register;
import com.partytrade.BFXWebSocket.Start;

/*
KERNEL
======
central component handling data from websocket feeds and managing
the dataflow to and from the database. this should be where all
state is managed
*/


public class Kernel extends AbstractActor {

  public static final class PriceRequest {}

  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
	BigDecimal currentPrice = new BigDecimal(0.0);
  ActorRef bfx;

  public static Props props(ActorRef bfx) {
  	return Props.create(Kernel.class, () -> new Kernel(bfx));
  }

  public Kernel(ActorRef _bfx) {
    bfx = _bfx;
    bfx.tell(new Register(), getSelf());
    bfx.tell(new Start(), getSelf());
  }

  private void consumePriceUpdate(PriceUpdate update) {
    this.currentPrice = update.price;
    log.info("PRICE: " + this.currentPrice);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
    	.match(PriceUpdate.class, update -> {
        consumePriceUpdate(update);
    	})
      .match(PriceRequest.class, request -> {
        getSender().tell(currentPrice, null);
      })
      .match(Order.class, order -> {
        log.info("Received an order: " + order.type + "(" + order.units + ") @ " + order.price + " for account " + order.accountId);
        getSender().tell(true, null);
      })
    	.build();
  }
}