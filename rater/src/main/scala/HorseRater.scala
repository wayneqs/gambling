import com.labfabulous.DayWorker.{OK, Start}
import com.labfabulous.Work
import com.mongodb.{BasicDBList, BasicDBObject}
import com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.MongoClient
import org.joda.time.DateTime

class HorseRater extends Work {
  RegisterJodaTimeConversionHelpers()
  private val mongoClient = MongoClient()("racing_data")

  def rate(runners: BasicDBList) {
  }

  def doWork(msg: Start, date: DateTime) = {

    val meetingsCollection = mongoClient("meetings")
    val q = MongoDBObject("url" -> """http://www\.sportinglife\.com/racing/results/.*""".r,
                          "date" -> date)
    for (
      meeting <- meetingsCollection.find(q);
      runners = meeting.get("race").asInstanceOf[BasicDBObject].get("runners").asInstanceOf[BasicDBList]
      if runners.size() > 1 // we are not interested in races with a single runner
    ) {
      rate(runners)
    }
    OK(msg, date)
  }
}
