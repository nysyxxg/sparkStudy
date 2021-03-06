import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat
import org.apache.hadoop.security.UserGroupInformation
import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._

/**
  * spark写入Kerberos下的hbase
  */
object spark_write_hbase {
  def main(args: Array[String]): Unit = {

    val conf = get_conf_and_login()


    val spark = SparkSession.builder().appName("read_hbase")
      .master("local[3]")
      .getOrCreate()
    val sc: SparkContext = spark.sparkContext
    import spark.implicits._
    sc.setLogLevel("WARN")

//    val df: DataFrame = spark.range(1000).withColumn("index", monotonically_increasing_id())

    val df=spark.read.option("header",true).csv("E:\\test\\retail")
      .where((col("STATUS")=!="04"))
      .na.drop("any",Seq("CITY","CUST_NAME"))
      .select("CUST_ID","CUST_NAME","GRADE","STATUS","CITY","SALE_CENTER_ID")

    df.printSchema()


    spark_write_hbase(df,conf,Map("table"->"TEST1","cf"->"0","row_key"->"CUST_ID"),List("CUST_NAME","STATUS","CITY","SALE_CENTER_ID"))


    sc.stop()

//    df.show(false)
  }

  def spark_write_hbase(df:DataFrame,conf:Configuration,param:Map[String,String],columns:List[String]): Unit ={
    /**
      * param "table"<-"","cf"<-"","row_key"<-""
      */

    val table=param("table")
    val cf=param("cf")
    val row_key=param("row_key")



    val job: Job = Job.getInstance(conf)
    job.setOutputFormatClass(classOf[TableOutputFormat[ImmutableBytesWritable]])
    job.setOutputKeyClass(classOf[ImmutableBytesWritable])
    job.setOutputValueClass(classOf[Put])
    val jobConf=job.getConfiguration
    jobConf.set(TableOutputFormat.OUTPUT_TABLE,param("table"))



    df.limit(1000).rdd.map(x=>{
      val put = new Put(Bytes.toBytes(x.getAs[String](row_key)))

      for(column <- columns){
        val value=x.getAs[String](column)
        //        println(column,value)
        //        try{
        put.addColumn(Bytes.toBytes(cf),Bytes.toBytes(column),Bytes.toBytes(value))
        //        }catch{
        //          case ex:Exception=>println("exception:",column,value)
        //        }

      }
      (new ImmutableBytesWritable,put)
    }).saveAsNewAPIHadoopDataset(jobConf)
  }

  def get_conf_and_login(): Configuration ={

    val krb5_conf = this.getClass.getResource("krb5.conf").getPath
    val user_keytab = this.getClass.getResource("zhangzy.keytab").getPath
    System.setProperty("java.security.krb5.conf", krb5_conf)

    val conf: Configuration = HBaseConfiguration.create()
    conf.set("hadoop.security.authentication", "Kerberos")
    //    conf.set(TableOutputFormat.OUTPUT_TABLE,"test_ma")

    UserGroupInformation.setConfiguration(conf)
    val ugi = UserGroupInformation.loginUserFromKeytabAndReturnUGI("zhangzy@HADOOP.COM", user_keytab)
    UserGroupInformation.setLoginUser(ugi)

    return conf
  }
}
