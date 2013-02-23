import akka.actor.{Props, ActorSystem}
import com.labfabulous.DayWorker.Start
import com.labfabulous.{Listener, Epocher, DayWorker}
import com.mongodb.casbah.MongoClient

object App {
  val system = ActorSystem("rater-system")

  def main(args: Array[String]) {
    val raterProps = Props(new HorseRater(MongoClient()))
    val listener = system.actorOf(Props[Listener], name = "rater-listener-actor")
    val rater = system.actorOf(Props(new DayWorker(MongoClient(), raterProps)), name = "n-rater")
    rater.tell(Start("n-rater", Epocher.get()), listener)
  }
}
