import akka.actor.{Props, ActorSystem}
import com.labfabulous.DayWorker.Start
import com.labfabulous.{Listener, Epocher, DayWorker}

object App {
  val system = ActorSystem("rater-system")


  def main(args: Array[String]) {
    val work = new HorseRater
    val listener = system.actorOf(Props[Listener], name = "rater-listener-actor")
    val raterActor = system.actorOf(Props(new DayWorker(work)), name = "rater-actor")
    raterActor.tell(Start("http://labfabulous.com/rating", Epocher.get()), listener)
  }
}
