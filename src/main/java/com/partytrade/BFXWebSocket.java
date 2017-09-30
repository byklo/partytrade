package com.partytrade;

import akka.actor.AbstractActor;
import akka.actor.AbstractActor.Receive;
import akka.actor.ActorSystem;
import akka.actor.Props;

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

import java.util.concurrent.CompletionStage;

import com.partytrade.model.PriceUpdate;


public class BFXWebSocket extends AbstractActor {
  ActorSystem system = this.getContext().system();
  Materializer materializer = ActorMaterializer.create(system);

  String subscriptionPayload = "{ `event`: `subscribe`, `channel`: `trades`, `symbol`: `BTCUSD` }".replace('`', '"');

  final Sink<Message, CompletionStage<Done>> printSink = Sink.foreach((message) ->
    System.out.println("Got message: " + message.asTextMessage().getStrictText())
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

  static Props props() {
  	return Props.create(BFXWebSocket.class, () -> new BFXWebSocket());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder().build();
  }
}


