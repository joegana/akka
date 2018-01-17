package jdocs.akka.cluster.typed;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorBehavior;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class ReceptionistExampleTest {

  public static class PingPongExample {
    //#ping-service
    static ServiceKey<Ping> PingServiceKey =
      ServiceKey.create(Ping.class, "pingService");

    public static class Pong {}
    public static class Ping {
      private final ActorRef<Pong> replyTo;
      Ping(ActorRef<Pong> replyTo) {
        this.replyTo = replyTo;
      }
    }

    static Behavior<Ping> pingService() {
      return ActorBehavior.deferred((ctx) -> {
        ctx.getSystem().receptionist()
          .tell(new Receptionist.Register<>(PingServiceKey, ctx.getSelf(), ctx.getSystem().deadLetters()));
        return ActorBehavior.immutable(Ping.class)
          .onMessage(Ping.class, (c, msg) -> {
            msg.replyTo.tell(new Pong());
            return ActorBehavior.same();
          }).build();
      });
    }
    //#ping-service

    //#pinger
    static Behavior<Pong> pinger(ActorRef<Ping> pingService) {
      return ActorBehavior.deferred((ctx) -> {
        pingService.tell(new Ping(ctx.getSelf()));
        return ActorBehavior.immutable(Pong.class)
          .onMessage(Pong.class, (c, msg) -> {
            System.out.println("I was ponged! " + msg);
            return ActorBehavior.same();
          }).build();
      });
    }
    //#pinger

    //#pinger-guardian
    static Behavior<Receptionist.Listing<Ping>> guardian() {
      return ActorBehavior.deferred((ctx) -> {
        ctx.getSystem().receptionist()
          .tell(new Receptionist.Subscribe<>(PingServiceKey, ctx.getSelf()));
        ActorRef<Ping> ps = ctx.spawnAnonymous(pingService());
        ctx.watch(ps);
        return ActorBehavior.immutable((c, msg) -> {
          msg.getServiceInstances().forEach(ar -> ctx.spawnAnonymous(pinger(ar)));
          return ActorBehavior.same();
        });
      });
    }
    //#pinger-guardian
  }

  public void workPlease() throws Exception {
    ActorSystem<Receptionist.Listing<PingPongExample.Ping>> system =
      ActorSystem.create(PingPongExample.guardian(), "ReceptionistExample");

    Await.ready(system.terminate(), Duration.create(2, TimeUnit.SECONDS));
  }
}
