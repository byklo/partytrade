package com.partytrade;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import akka.http.javadsl.server.HttpApp;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.HttpOrigin;
import akka.http.javadsl.model.headers.HttpOriginRange;
import akka.pattern.Patterns;
import static akka.http.javadsl.unmarshalling.Unmarshaller.entityToString;
import ch.megard.akka.http.cors.javadsl.settings.CorsSettings;
import static ch.megard.akka.http.cors.javadsl.CorsDirectives.cors;
import static ch.megard.akka.http.cors.javadsl.CorsDirectives.corsRejectionHandler;

import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import akka.util.Timeout;

import com.partytrade.Kernel.PriceRequest;
import com.partytrade.model.Order;

import com.google.gson.Gson;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/*
ENDPOINT
========
the http server. handles requests from front-end.
*/


public class Endpoint {
  final ActorRef kernel;
  final HttpServer server;

  class HttpServer extends HttpApp {
    @Override
    protected Route routes() {

      final CorsSettings settings = CorsSettings.defaultSettings();
      final RejectionHandler rejectionHandler = corsRejectionHandler().withFallback(RejectionHandler.defaultHandler());
      final ExceptionHandler exceptionHandler = ExceptionHandler.newBuilder()
        .match(NoSuchElementException.class, ex -> complete(StatusCodes.NOT_FOUND, ex.getMessage()))
        .build();
      final Function<Supplier<Route>, Route> handleErrors = inner -> Directives.allOf(
        s -> handleExceptions(exceptionHandler, s),
        s -> handleRejections(rejectionHandler, s),
        inner
      );

      return cors(settings, () -> handleErrors.apply(() -> route(
        post( () ->
          path("trade", () ->
            // curl --data "{'type': 'LIMIT', 'units': '-50', 'accountId': '32158315', 'price': '4324'}" localhost:8080/trade
            // curl --data "{'type': 'MARKET', 'units': '2', 'accountId': '32158315', 'price': '0'}" localhost:8080/trade
            entity(entityToString(), body -> {
              Gson gson = new Gson();
              Order order;
              try {
                order = gson.fromJson(body, Order.class);
              } catch (Exception e) {
                return complete("Failed to parse JSON order");
              }
              Timeout timeout = new Timeout(Duration.create(5, "seconds"));
              Future<Object> fSuccess = Patterns.ask(kernel, order, timeout);
              boolean success;
              try {
                success = (boolean) Await.result(fSuccess, timeout.duration());
              } catch (Exception e) {
                return complete("Failed to process order");
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
      )));
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






