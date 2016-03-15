package telemetry.test

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.hive.HiveContext
import org.scalatest.{FlatSpec, Matchers}
import telemetry.views.KPIView
import telemetry.spark.sql.functions._

case class Submission(clientId: String,
                      normalizedChannel: String,
                      submissionDate: String,
                      subsessionStartDate: String,
                      country: String,
                      version: String,
                      e10sEnabled: Boolean,
                      e10sCohort: String)

object Submission{
  val dimensions = Map("clientId" -> List("x", "y", "z"),
                       "normalizedChannel" -> List("release", "nightly"),
                       "submissionDate" -> List("20160107", "20160106"),
                       "subsessionStartDate" -> List("2016-03-13T00:00:00.0+01:00"),
                       "country" -> List("IT", "US"),
                       "version" -> List("44"),
                       "e10sEnabled" -> List(true, false),
                       "e10sCohort" -> List("control", "test"))

  def randomList: List[Submission] = {
    for {
      clientId <- dimensions("clientId")
      normalizedChannel <- dimensions("normalizedChannel")
      submissionDate <- dimensions("submissionDate")
      subsessionStartDate <- dimensions("subsessionStartDate")
      country <- dimensions("country")
      version <- dimensions("version")
      e10sEnabled <- dimensions("e10sEnabled")
      e10sCohort <- dimensions("e10sCohort")
    } yield {
      Submission(clientId.asInstanceOf[String],
                 normalizedChannel.asInstanceOf[String],
                 submissionDate.asInstanceOf[String],
                 subsessionStartDate.asInstanceOf[String],
                 country.asInstanceOf[String],
                 version.asInstanceOf[String],
                 e10sEnabled.asInstanceOf[Boolean],
                 e10sCohort.asInstanceOf[String])
    }
  }
}

class KPITest extends FlatSpec with Matchers{
  "Dataset" can "be aggregated" in {
    val sparkConf = new SparkConf().setAppName("KPI")
    sparkConf.setMaster(sparkConf.get("spark.master", "local[1]"))
    val sc = new SparkContext(sparkConf)
    val sqlContext = new HiveContext(sc)
    import sqlContext.implicits._

    val dataset = sc.parallelize(Submission.randomList).toDF()
    val aggregates = KPIView.aggregate(dataset)

    val dimensions = Set(KPIView.dimensions:_*) -- Set("clientId", "hll")
    (Set(aggregates.columns:_*) -- Set("clientId", "hll")) should be (dimensions)

    val estimates = aggregates.select(hll_cardinality(col("hll"))).collect()
    estimates.foreach(x => x(0) should be (Submission.dimensions("clientId").size))
  }
}
