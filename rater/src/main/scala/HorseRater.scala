import akka.actor.Actor
import com.labfabulous.DayWorker.WorkForDate
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.query.Imports._

class HorseRater(mongo: MongoClient) extends Actor {
  RegisterJodaTimeConversionHelpers()
  private val db = mongo("racing_data")

  def doWork(w: WorkForDate) = {

    def rate(runners: BasicDBList) {
    }

    val meetingsCollection = db("meetings")
    val q = MongoDBObject("category" -> "results", "date" -> w.date.toDateTimeAtStartOfDay)
    for (meeting <- meetingsCollection.find(q);
         runners = meeting.get("race").asInstanceOf[BasicDBObject].get("runners").asInstanceOf[BasicDBList]
         if runners.size() > 1) { // not interested in races where there is only a single runner
      rate(runners)
    }
  }

  def receive = {
    case w: WorkForDate => doWork(w)
  }
}
