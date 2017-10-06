package com.partytrade;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import akka.stream.Materializer;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;

import akka.http.javadsl.Http;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.model.ws.WebSocketUpgradeResponse;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.util.concurrent.CompletionStage;
import java.io.StringReader;
import java.io.IOException;
import java.math.BigDecimal;

import com.partytrade.model.PriceUpdate;

/*
BFXWEBSOCKET
============
creates an actor that holds a websocket connection to bfx. pipes data
to the server. NO data handling is done here
*/


public class BFXWebSocket extends AbstractActor {
  LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

  public static final class Register {}

  public static final class Start {}

  ActorRef server;

  static Props props() {
    return Props.create(BFXWebSocket.class, () -> new BFXWebSocket());
  }

  private void consumeMessage(String msg) {
    // [77,"hb"]
    // [77,"te","13374089-BTCUSD",1507252326,4333.4,0.25591912]
    // [77,"tu","13374089-BTCUSD",73621743,1507252326,4333.4,0.25591912]
    try {
      JsonReader reader = new JsonReader(new StringReader(msg));
      if (reader.peek().equals(JsonToken.BEGIN_ARRAY)) {
        reader.beginArray();
        if (! reader.peek().equals(JsonToken.NUMBER)) {
          throw new IOException();
        }
        reader.nextDouble();
        if (! reader.peek().equals(JsonToken.STRING)) {
          throw new IOException();
        }
        String messageType = reader.nextString();
        if (! messageType.equals("te")) {
          throw new IOException();
        }
        reader.nextString();
        reader.nextDouble();
        Double dPrice = reader.nextDouble();
        BigDecimal price = BigDecimal.valueOf(dPrice);
        server.tell(new PriceUpdate(price), getSelf());
      } else {
        throw new IOException();
      }
    } catch (IOException e) {
      // log.info("could not parse message: " + msg);
    } catch (Exception e) {
      // log.info("could not parse message: " + msg);
    }
  }

  private void playTrades() {
    ActorSystem system = this.getContext().system();
    Materializer materializer = ActorMaterializer.create(system);

    String subscriptionPayload = "{ `event`: `subscribe`, `channel`: `trades`, `symbol`: `BTCUSD` }".replace('`', '"');
    // String subscriptionPayload = "{ `event`: `subscribe`, `channel`: `book`, `pair`: `BTCUSD` }".replace('`', '"');

    final Sink<Message, CompletionStage<Done>> printSink = Sink.foreach((message) ->
      // System.out.println("Got message: " + message.asTextMessage().getStrictText())
      consumeMessage(message.asTextMessage().getStrictText())
    );

    final Source<Message, NotUsed> subscription = Source.single(TextMessage.create(subscriptionPayload)).concatMat(Source.maybe(), Keep.left());

    final Flow<Message, Message, CompletionStage<Done>> flow = Flow.fromSinkAndSourceMat(
      printSink,
      subscription,
      Keep.left()
    );

    final Pair<CompletionStage<WebSocketUpgradeResponse>, CompletionStage<Done>> pair = Http.get(system).singleWebSocketRequest(
      WebSocketRequest.create("wss://api.bitfinex.com/ws"),
      flow,
      materializer
    );

    final CompletionStage<Done> connected = pair.first().thenApply(upgrade -> {
      if (upgrade.response().status().equals(StatusCodes.SWITCHING_PROTOCOLS)) {
        return Done.getInstance();
      } else {
        throw new RuntimeException("Connection failed: " + upgrade.response().status());
      }
    });

    final CompletionStage<Done> closed = pair.second();

  	{
    	connected.thenAccept(x -> System.out.println("Connected"));
    	closed.thenAccept(x -> System.out.println("Connection closed"));
  	}
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Register.class, x -> {
        server = getSender();
      })
      .match(Start.class, x -> {
        playTrades();
      })
      .build();
  }
}




