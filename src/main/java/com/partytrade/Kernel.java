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
  }

  private boolean consumeOrder(Order order) {
    boolean response = true;
    switch (order.type) {
      case "MARKET":
        log.info("Executing MARKET order for (" + order.units + ") @ " + currentPrice + " for account " + order.accountId);
        break;
      case "LIMIT":
        log.info("Placing LIMIT order for (" + order.units + ") @ " + order.price + " for account " + order.accountId);
        break;
      default:
        log.info("INVALID ORDER TYPE RECEIVED: " + order.type);
        response = false;
        break;
    }
    return response;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(PriceUpdate.class, update -> {
        consumePriceUpdate(update);
        log.info("[TICK] " + update.price);
    	})
      .match(PriceRequest.class, request -> {
        getSender().tell(currentPrice, null);
      })
      .match(Order.class, order -> {
        // handle the order!
        // commit to db or whatever
        // should probably query the user's balance, and also most up to date price
        // if its a market order, allow slippage LOL
        log.info("Received an order: " + order.type + "(" + order.units + ") @ " + order.price + " for account " + order.accountId);
        boolean orderResponse = consumeOrder(order);
        getSender().tell(orderResponse, null);
      })
    	.build();
  }
}