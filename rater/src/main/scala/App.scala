import akka.actor.{Props, ActorSystem}
import com.labfabulous._
import com.mongodb.casbah.MongoClient
import http.Downloader.Get
import http.{HttpThrottler, Downloader}

object App {
  val system = ActorSystem("rater-system")

  def main(args: Array[String]) {
//    val raterProps = Props(new HorseRater(MongoClient()))
//    val listener = system.actorOf(Props[Listener], name = "rater-listener-actor")
//    val rater = system.actorOf(Props(new DayWorker(MongoClient(), raterProps)), name = "n-rater")
//    rater.tell(Start("n-rater", Epocher.get()), listener)
    val downloaderProps = Props(new Downloader(MongoClient(), new HttpThrottler[String]("")))
    val listener = system.actorOf(Props[ProgressListener], name = "rater-listener-actor")
    val dayWorker = system.actorOf(Props(new DayWorker(MongoClient(), downloaderProps)), name = "day-worker")
    new LinksExtractor
    dayWorker.tell(Get(), listener)
  }
}
