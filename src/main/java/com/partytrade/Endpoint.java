package com.partytrade;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.Route;
import akka.pattern.Patterns;
import static akka.http.javadsl.unmarshalling.Unmarshaller.entityToString;
import akka.http.javadsl.server.directives.WebSocketDirectives;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.util.Timeout;
import akka.NotUsed;
import akka.japi.JavaPartialFunction;

import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import com.partytrade.Kernel.PriceRequest;
import com.partytrade.model.Order;

import com.google.gson.Gson;

import java.math.BigDecimal;

/*
ENDPOINT
========
the http server. handles requests from front-end.
- when you connect to localhost:8080/ws
  - you should be auto subscribed to a price feed
  - you should be able to send messages to the server
    - execute trade
*/


public class Endpoint {
  final ActorRef kernel;
  final HttpServer server;

  public static Flow<Message, Message, NotUsed> websocketServer() {
    return Flow.<Message>create().collect(new JavaPartialFunction<Message, Message>() {
      @Override
      public Message apply(Message msg, boolean isCheck) throws Exception {
        if (isCheck) {
          if (msg.isText()) {
            return null;
          } else {
            System.out.println("websocketServer threw!");
            throw noMatch();
          }
        } else {
          return handleWebSocketMessage(msg.asTextMessage());
        }
      }
    });
  }

  public static TextMessage handleWebSocketMessage(TextMessage msg) {
    if (msg.isStrict()) // optimization that directly creates a simple response...
    {
      return TextMessage.create("Hello " + msg.getStrictText());
    } else // ... this would suffice to handle all text messages in a streaming fashion
    {
      return TextMessage.create(Source.single("Hello ").concat(msg.getStreamedText()));
    }
  }

  class HttpServer extends HttpApp {
    @Override
    protected Route routes() {
      return route(
        path("ws", () ->
          handleWebSocketMessages(websocketServer())
        ),
        post( () ->
          path("trade", () ->
            // curl --data "{'type': 'LIMIT', 'units': '-50', 'accountId': '32158315', 'price': '4324'}" localhost:8080/trade
            entity(entityToString(), body -> {
              Gson gson = new Gson();
              Order order;
              try {
                order = gson.fromJson(body, Order.class);
              } catch (Exception e) {
                return complete("Failed to parse JSON");
              }
              Timeout timeout = new Timeout(Duration.create(5, "seconds"));
              Future<Object> fSuccess = Patterns.ask(kernel, order, timeout);
              boolean success;
              try {
                success = (boolean) Await.result(fSuccess, timeout.duration());
              } catch (Exception e) {
                return complete("Failed to prcess order");
              }
              return complete(success ? "SUCCESS" : "REJECTED");
            })
          )
        ),
        get( () ->
          // curl localhost:8080/price
          path("price", () -> {
            Timeout timeout = new Timeout(Duration.create(5, "seconds"));
            Future<Object> fPrice = Patterns.ask(kernel, new PriceRequest(), timeout);
            BigDecimal price;
            try {
              price = (BigDecimal) Await.result(fPrice, timeout.duration());
            } catch (Exception e) {
              System.out.println("Could not cast a price request");
              return complete("ERROR");
            }
            return complete(price.toString());
          })
        )
      );
    }
  }

  public Endpoint(ActorRef _kernel) {
    kernel = _kernel;
    server = new HttpServer();
  }

  public void run(ActorSystem system) {
    try {
      server.startServer("localhost", 8080, system);
    } catch (Exception e) {
      System.out.println("SERVER CRASHED");
    }
  }
}






