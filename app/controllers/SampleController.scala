package controllers

import java.io.File
import javax.inject.Inject

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import dao._
import models.Tables._
import org.apache.commons.io.FileUtils
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import utils._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration


class SampleController @Inject()(admindao: adminDao, projectdao: projectDao, sampledao: sampleDao) extends Controller {

  def enterHome(projectname: String): Action[AnyContent] = Action { implicit request =>
    val userId = request.session.get("id").head.toInt
    val projectId = Await.result(projectdao.getIdByProjectname(userId, projectname), Duration.Inf)
    val data = new File(Utils.path + "/" + userId + "/" + projectId + "/data")
    val allName = Await.result(projectdao.getAllProject(userId), Duration.Inf)
    if (data.listFiles().size < 2) {
      Redirect(routes.SampleController.loadData(projectname))
    } else {
      Redirect(routes.SampleController.dataPage(projectname))
    }
  }


  def loadData(proname: String): Action[AnyContent] = Action { implicit request =>
    val userId = request.session.get("id").head.toInt
    val allName = Await.result(projectdao.getAllProject(userId), Duration.Inf)
    Ok(views.html.fileupload.uploadPE(allName, proname,request.domain))
  }

  def toSE(proname:String) : Action[AnyContent] = Action{ implicit request=>
    val userId = request.session.get("id").head.toInt
    val allName = Await.result(projectdao.getAllProject(userId), Duration.Inf)
    Ok(views.html.fileupload.uploadSE(allName, proname,request.domain))
  }

  def home = Action { implicit request =>
    val userId = request.session.get("id").head.toInt
    val all = Await.result(projectdao.getAll(userId), Duration.Inf)
    val projectname = all.map(_.projectname)
    Ok(views.html.background.home(all, projectname,request.domain))
  }

  case class paraData(proname: String, sample: String, encondingType: String, stepMethod: String, adapter: Option[String],
                      seed_mismatches: Option[Int], palindrome_clip_threshold: Option[Int],
                      simple_clip_threshold: Option[Int], trimMethod: String, window_size: Option[Int],
                      required_quality: Option[Int], minlenMethod: String, minlen: Option[Int],
                      leadingMethod: String, leading: Option[Int], trailingMethod: String, trailing: Option[Int],
                      cropMethod: String, crop: Option[Int], headcropMethod: String, headcrop: Option[Int])


  val paraForm = Form(
    mapping(
      "proname" -> text,
      "sample" -> text,
      "encondingType" -> text,
      "stepMethod" -> text,
      "adapter" -> optional(text),
      "seed_mismatches" -> optional(number),
      "palindrome_clip_threshold" -> optional(number),
      "simple_clip_threshold" -> optional(number),
      "trimMethod" -> text,
      "window_size" -> optional(number),
      "required_quality" -> optional(number),
      "minlenMethod" -> text,
      "minlen" -> optional(number),
      "leadingMethod" -> text,
      "leading" -> optional(number),
      "trailingMethod" -> text,
      "trailing" -> optional(number),
      "cropMethod" -> text,
      "crop" -> optional(number),
      "headcropMethod" -> text,
      "headcrop" -> optional(number)
    )(paraData.apply)(paraData.unapply)
  )

  private def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType) =>

      val file = new File(Utils.tmpPath , Utils.random)
      val path = file.toPath
      val fileSink: Sink[ByteString, Future[IOResult]] = FileIO.toPath(path)
      val accumulator: Accumulator[ByteString, IOResult] = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          FilePart(partName, filename, contentType, file)
      }
  }


  def uploadPE = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    val path = Utils.path
    val file = request.body.files
    val paradata = paraForm.bindFromRequest.get
    val proname = paradata.proname
    val userId = request.session.get("id").head.toInt
    val project = Await.result(projectdao.getProject(userId, proname), Duration.Inf)
    val type1 = paradata.encondingType
    val type2 = "-"+type1
    val sample = paradata.sample
    val date = Utils.date
    val sa = SampleRow(0, sample, userId, project.id, date, "PE", 0)
    Await.result(sampledao.addSample(Seq(sa)), Duration.Inf)
    val row = Await.result(sampledao.getByPosition(userId, project.id, sample), Duration.Inf)
    try {
      val run = Future {
        val outPath = Utils.outPath(userId, project.id, row.id)
        val in1 = file.head.ref.getPath
        val name1 = file.head.filename
        val in2 = file(1).ref.getPath
        val name2 = file(1).filename
        val out1 = outPath + "/raw.data_1.fastq"
        val out2 = outPath + "/raw.data_2.fastq"
        getFastq(in1, out1, name1)
        getFastq(in2, out2, name2)
        new File(outPath + "/tmp").mkdir()
        val deploy = mutable.Buffer(row.id, type1, paradata.stepMethod, paradata.adapter.get, paradata.seed_mismatches.getOrElse(2),
          paradata.palindrome_clip_threshold.getOrElse(30), paradata.simple_clip_threshold.getOrElse(10), paradata.trimMethod,
          paradata.window_size.getOrElse(20), paradata.required_quality.getOrElse(20), paradata.minlenMethod, paradata.minlen.getOrElse(35),
          paradata.leadingMethod, paradata.leading.getOrElse(3), paradata.trailingMethod, paradata.trailing.getOrElse(20),
          paradata.cropMethod, paradata.crop.getOrElse(0), paradata.headcropMethod, paradata.headcrop.getOrElse(0))

        FileUtils.writeLines(new File(outPath + "/deploy.txt"), deploy.asJava)
        runCmd1(row, "PE")

      }
    }catch{
      case e : Exception => Await.result(sampledao.updateState(row.id,2),Duration.Inf)
    }

    Ok(Json.obj("valid" -> "true"))
  }

  def uploadSE = Action(parse.multipartFormData(handleFilePartAsFile)) { implicit request =>
    val path = Utils.path
    val file= request.body.file("file").get
    val paradata = paraForm.bindFromRequest.get
    val proname = paradata.proname
    val userId = request.session.get("id").head.toInt
    val project = Await.result(projectdao.getProject(userId, proname), Duration.Inf)
    val type1 = paradata.encondingType
    val type2 = "-"+type1
    val sample = paradata.sample
    val date = Utils.date
    val sa = SampleRow(0, sample, userId, project.id, date, "SE", 0)
    Await.result(sampledao.addSample(Seq(sa)), Duration.Inf)
    val row = Await.result(sampledao.getByPosition(userId, project.id, sample), Duration.Inf)
    try {
      val run = Future {
        val outPath = Utils.outPath(userId, project.id, row.id)
        val in = file.ref.getPath
        val name = file.filename
        val out = outPath + "/raw.data.fastq"
        getFastq(in, out, name)
        new File(outPath + "/tmp").mkdir()
        val deploy = mutable.Buffer(row.id, type1, paradata.stepMethod, paradata.adapter.get, paradata.seed_mismatches.getOrElse(2),
          paradata.palindrome_clip_threshold.getOrElse(30), paradata.simple_clip_threshold.getOrElse(10), paradata.trimMethod,
          paradata.window_size.getOrElse(20), paradata.required_quality.getOrElse(20), paradata.minlenMethod, paradata.minlen.getOrElse(35),
          paradata.leadingMethod, paradata.leading.getOrElse(3), paradata.trailingMethod, paradata.trailing.getOrElse(20),
          paradata.cropMethod, paradata.crop.getOrElse(0), paradata.headcropMethod, paradata.headcrop.getOrElse(0))
        FileUtils.writeLines(new File(outPath + "/deploy.txt"), deploy.asJava)
        runCmd1(row, "SE")
      }
    }catch{
      case e : Exception => Await.result(sampledao.updateState(row.id,2),Duration.Inf)
    }
    Ok(Json.obj("valid" -> "true"))
  }

  def resetPE = Action { implicit request =>
    val path = Utils.path
    val paradata = paraForm.bindFromRequest.get
    val proname = paradata.proname
    val userId = request.session.get("id").head.toInt
    val project = Await.result(projectdao.getProject(userId, proname), Duration.Inf)
    val type1 = paradata.encondingType
    val type2 = "-"+type1
    val sample = paradata.sample
    val date = Utils.date
    val sampleRow =Await.result(sampledao.getByPosition(userId,project.id,sample),Duration.Inf)
    Await.result(sampledao.updateState(sampleRow.id,0), Duration.Inf)
    val outPath = Utils.outPath(userId,project.id,sampleRow.id)
    new File(outPath + "/tmp").mkdir()
    val deploy = mutable.Buffer(sampleRow.id, type1, paradata.stepMethod, paradata.adapter.get, paradata.seed_mismatches.getOrElse(2),
      paradata.palindrome_clip_threshold.getOrElse(30), paradata.simple_clip_threshold.getOrElse(10), paradata.trimMethod,
      paradata.window_size.getOrElse(20), paradata.required_quality.getOrElse(20), paradata.minlenMethod, paradata.minlen.getOrElse(35),
      paradata.leadingMethod, paradata.leading.getOrElse(3), paradata.trailingMethod, paradata.trailing.getOrElse(20),
      paradata.cropMethod, paradata.crop.getOrElse(0), paradata.headcropMethod, paradata.headcrop.getOrElse(0))//69
    FileUtils.writeLines(new File(outPath + "/deploy.txt"), deploy.asJava)
    Ok(Json.obj("valid" -> "true","id" -> sampleRow.id))
  }

  def runCmd1(row: SampleRow,inputType:String) = {
    val outPath = Utils.outPath(row.accountid, row.projectid, row.id)
    val deploy = FileUtils.readLines(new File(outPath, "deploy.txt")).asScala
    val project = Await.result(projectdao.getById(row.projectid),Duration.Inf)
    val (command1,command2) = if(inputType == "PE"){
      val fastqc = s"${Utils.toolPath}/FastQC/fastqc ${outPath}/raw.data_1.fastq ${outPath}/raw.data_2.fastq -o ${outPath}/"
      (PETrimmomatic(outPath, deploy),fastqc)
    }else{
      val fastqc = s"${Utils.toolPath}/FastQC/fastqc ${outPath}/raw.data.fastq -o ${outPath}/"
      (SETrimmomatic(outPath, deploy),fastqc)
    }
    val command = new ExecCommand
    val tmp = outPath + "/tmp"
    command.exect(command1,command2,tmp)
    val samples = Await.result(sampledao.getAllSample(row.accountid, row.projectid), Duration.Inf)
    Await.result(projectdao.updateCount(row.projectid, samples.size), Duration.Inf)
    if (command.isSuccess) {
      FileUtils.deleteDirectory(new File(tmp))
      Await.result(sampledao.updateState(row.id,1), Duration.Inf)
      FileUtils.writeStringToFile(new File(outPath, "log.txt"), command.getErrStr)
      if(inputType == "PE"){
        getLog(outPath, command.getErrStr)
      }else{
        getSELog(outPath, command.getErrStr)
      }
    } else {
      FileUtils.deleteDirectory(new File(tmp))
      Await.result(sampledao.updateState(row.id,2), Duration.Inf)
      if(new File(outPath).exists()){
        getSELog(outPath, command.getErrStr)
      }
    }
  }

  def getFastq(path: String, outputPath: String,name:String): Unit = {
    val suffix = name.split('.').last
    if (suffix == "gz") {
      FileUtils.writeStringToFile(new File(outputPath),"")
      CompactAlgorithm.unGzipFile(path, outputPath)
      new File(path).delete()
    } else {
      FileUtils.moveFile(new File(path), new File(outputPath))
    }
  }

  def getSeqs(outPath: String): Int = {
    val fastq = outPath + "/out.extendedFrags.fastq"
    val fasta = outPath + "/out_file.fasta"
    dealFasta.fqToFa(fastq, fasta)
    val seq = FileUtils.readLines(new File(fasta)).asScala
    new File(fastq).delete()
    val seqs = seq.size / 2
    seqs
  }

  def getSELog(outPath: String,output:String) : Unit = {
    val tri = output.split("\n").drop(1).toBuffer
    val logs = tri
    FileUtils.writeLines(new File(outPath, "log.txt"), logs.asJava)
  }

  def getLog(outPath: String, output: String): Unit = {
    val trans = output.split("Input Read Pairs")
    val input = ("Input Read Pairs" + trans.drop(1).head).split("Both Surviving")
    val both = ("Both Surviving" + input.drop(1).head).split("Forward Only Surviving")
    val forward = ("Forward Only Surviving" + both.drop(1).head).split("Reverse Only Surviving")
    val reverse = ("Reverse Only Surviving" + forward.drop(1).head).split("Dropped")
    val drop = ("Dropped" + reverse.drop(1).head).split("TrimmomaticPE")
    val PE = "TrimmomaticPE" + drop.drop(1).head
    val tri = mutable.Buffer(input.head, both.head, forward.head, reverse.head, drop.head, PE)
    val logs = tri
    FileUtils.writeLines(new File(outPath, "log.txt"), logs.asJava)
  }

  def PETrimmomatic(outPath: String, data: mutable.Buffer[String]): String = {
    val path = Utils.toolPath
    val in1 = outPath + "/raw.data_1.fastq"
    val in2 = outPath + "/raw.data_2.fastq"
    val tmpDir = outPath + "/tmp"
    val out1 = outPath + "/r1_paired_out.fastq"
    val unout1 = tmpDir + "/r1_unpaired_out.fastq"
    val out2 = outPath + "/r2_paired_out.fastq"
    val unout2 = tmpDir + "/r2_unpaired_out.fastq"
    var command = s"java -jar ${path}/Trimmomatic-0.32/trimmomatic-0.32.jar PE -threads 1 " +
      s"${data(1)} ${in1} ${in2} ${out1} ${unout1} ${out2} ${unout2} "
    if (data(2) == "yes") {
      val adapter = path + "/Trimmomatic-0.32/adapters/" + data(3)
      command += s"ILLUMINACLIP:${adapter}:${data(4)}:${data(5)}:${data(6)} "
    }
    if (data(7) == "yes") {
      command += s"SLIDINGWINDOW:${data(8)}:${data(9)} "
    }
    if (data(10) == "yes") {
      command += s"MINLEN:${data(11)} "
    }
    if (data(12) == "yes") {
      command += s"LEADING:${data(13)} "
    }
    if (data(14) == "yes") {
      command += s"TRAILING:${data(15)} "
    }
    if (data(16) == "yes") {
      command += s"CROP:${data(17)} "
    }
    if (data(18) == "yes") {
      command += s"HEADCROP:${data(19)} "
    }
    command
  }

  def SETrimmomatic(outPath: String, data: mutable.Buffer[String]): String = {
    val path = Utils.toolPath
    val in = outPath + "/raw.data.fastq"
    val out= outPath + "/raw_se_out.fastq"
    var command = s"java -jar ${path}/Trimmomatic-0.32/trimmomatic-0.32.jar SE -threads 1 " +
      s"${data(1)} ${in}  ${out}  "
    if (data(2) == "yes") {
      val adapter = path + "/Trimmomatic-0.32/adapters/" + data(3)
      command += s"ILLUMINACLIP:${adapter}:${data(4)}:${data(5)}:${data(6)} "
    }
    if (data(7) == "yes") {
      command += s"SLIDINGWINDOW:${data(8)}:${data(9)} "
    }
    if (data(10) == "yes") {
      command += s"MINLEN:${data(11)} "
    }
    if (data(12) == "yes") {
      command += s"LEADING:${data(13)} "
    }
    if (data(14) == "yes") {
      command += s"TRAILING:${data(15)} "
    }
    if (data(16) == "yes") {
      command += s"CROP:${data(17)} "
    }
    if (data(18) == "yes") {
      command += s"HEADCROP:${data(19)} "
    }
    command
  }

  def dataPage(proname: String) = Action { implicit request =>
    val id = getUserIdAndProId(request.session, proname)
    val allName = Await.result(projectdao.getAllProject(id._1), Duration.Inf)
    Ok(views.html.fileupload.data(allName, proname,request.domain))
  }

  def isRunCmd(id:Int): Action[AnyContent] = Action.async { implicit request =>
    sampledao.getAllById(id).map{x=>
          runCmd1(x,x.inputType)
      Ok(Json.obj("valid" -> "true"))
    }
  }

  case class updateSampleData(sampleId: Int, newsample: String)

  val updateSampleForm = Form(
    mapping(
      "sampleId" -> number,
      "newsample" -> text
    )(updateSampleData.apply)(updateSampleData.unapply)
  )

  def updateSample: Action[AnyContent] = Action { implicit request =>
    val data = updateSampleForm.bindFromRequest.get
    val newsample = data.newsample
    val sampleId = data.sampleId
    Await.result(sampledao.updateSample(sampleId, newsample), Duration.Inf)
    val json = Json.obj("valid" -> "true")
    Ok(Json.toJson(json))
  }

  def deleteSample(id: Int): Action[AnyContent] = Action { implicit request =>
    val ses = Await.result(sampledao.getAllById(id), Duration.Inf)
    Await.result(sampledao.deleteSample(id), Duration.Inf)
    val count = Await.result(sampledao.getAllSample(ses.accountid, ses.projectid), Duration.Inf)
    Await.result(projectdao.updateCount(ses.projectid, count.size), Duration.Inf)
    val run = Future{
      val path = Utils.outPath(ses.accountid, ses.projectid, id)
      FileUtils.deleteDirectory(new File(path))
    }
    val json = Json.obj("valid" -> "true")
    Ok(Json.toJson(json))
  }

  def deployGet(id: Int) = Action { implicit request =>
    val row = Await.result(sampledao.getAllById(id), Duration.Inf)
    val path = Utils.outPath(row.accountid,row.projectid,row.id)
    val pro = Await.result(projectdao.getById(row.projectid),Duration.Inf)
    val deploy = FileUtils.readLines(new File(path, "deploy.txt")).asScala
    val inputType = row.inputType
    val json = Json.obj("proname" -> pro.projectname, "id" -> deploy.head, "sample" -> row.sample, "encondingType" ->deploy(1),
        "stepMethod" -> deploy(2), "adapter" ->  deploy(3), "seed_mismatches"  -> deploy(4),
        "palindrome_clip_threshold"  -> deploy(5), "simple_clip_threshold"  -> deploy(6), "trimMethod" -> deploy(7),
        "window_size"  -> deploy(8), "required_quality"  -> deploy(9), "minlenMethod"  -> deploy(10),
        "minlen"  -> deploy(11), "leadingMethod"  -> deploy(12), "leading"  -> deploy(13), "trailingMethod"  -> deploy(14),
        "trailing"  -> deploy(15), "cropMethod"  -> deploy(16), "crop"  -> deploy(17), "headcropMethod"  -> deploy(18),
        "headcrop"  -> deploy(19),"inputType" -> inputType)//69

    Ok(Json.toJson(json))
  }

  def downloadPE(id: Int, code: Int) = Action { implicit request =>
    val row = Await.result(sampledao.getAllById(id), Duration.Inf)
    val path = Utils.outPath(row.accountid, row.projectid, id)
    val (file, name) = if (code == 1) {
      (new File(path, "raw.data_1.fastq"), row.sample + "_1.fastq")
    } else if (code == 2) {
      (new File(path, "raw.data_2.fastq"), row.sample + "_2.fastq")
    } else {
      (new File(path, "hisat2_map_sorted.bam"), row.sample + ".bam")
    }
    Ok.sendFile(file).withHeaders(
      CACHE_CONTROL -> "max-age=3600",
      CONTENT_DISPOSITION -> ("attachment; filename=" + name),
      CONTENT_TYPE -> "application/x-download"
    )
  }

  def downloadSE(id: Int,code: Int) = Action{implicit request=>
    val row = Await.result(sampledao.getAllById(id), Duration.Inf)
    val path = Utils.outPath(row.accountid, row.projectid, id)
    val (file, name) = if (code == 1) {
      (new File(path, "raw.data.fastq"), row.sample + ".fastq")
    } else {
      (new File(path, "hisat2_map_sorted.bam"), row.sample + ".bam")
    }
    Ok.sendFile(file).withHeaders(
      CACHE_CONTROL -> "max-age=3600",
      CONTENT_DISPOSITION -> ("attachment; filename=" + name),
      CONTENT_TYPE -> "application/x-download"
    )
  }

  def getUserIdAndProId(session: Session, proname: String): (Int, Int) = {
    val userId = session.get("id").head.toInt
    val proId = Await.result(projectdao.getIdByProjectname(userId, proname), Duration.Inf)
    (userId, proId)
  }

  def openLogFile(id: Int): Action[AnyContent] = Action { implicit request =>
    val row = Await.result(sampledao.getAllById(id), Duration.Inf)
    val path = Utils.outPath(row.accountid,row.projectid,id)
    val log = FileUtils.readLines(new File(path,"/log.txt")).asScala
    var html =
      """
        |<style>
        |   .logClass{
        |       font-size : 16px;
        |       font-weight:normal;
        |   }
        |</style>
      """.stripMargin
    html += "<b class='logClass'>" + log.mkString("</b><br><b class='logClass'>") + "</b>"
    val json = Json.obj("log" -> html)
    Ok(Json.toJson(json))
  }

  case class newsampleData(newsample: String)

  val newsampleForm = Form(
    mapping(
      "newsample" -> text
    )(newsampleData.apply)(newsampleData.unapply)
  )

  def checkNewsample(proname:String) = Action.async { implicit request =>
    val ses = getUserIdAndProId(request.session,proname)
    val data = newsampleForm.bindFromRequest.get
    val newsample = data.newsample
      sampledao.getByP(ses._1, ses._2, newsample).map { y =>
        val valid = if (y.size == 0) {
          "true"
        } else {
          "false"
        }
        Ok(Json.obj("valid" -> valid))
      }
  }

  case class sampleData(sample: String)

  val sampleForm = Form(
    mapping(
      "sample" -> text
    )(sampleData.apply)(sampleData.unapply)
  )

  def checkSample(proname: String) = Action.async { implicit request =>
    val ses = getUserIdAndProId(request.session, proname)
    val data = sampleForm.bindFromRequest.get
    val sample = data.sample
    sampledao.getByP(ses._1, ses._2, sample).map { y =>
      val valid = if (y.size == 0) {
        "true"
      } else {
        "false"
      }
      Ok(Json.obj("valid" -> valid))
    }
  }

  def checkRef(proname: String) = Action.async { implicit request =>
    val id = getUserIdAndProId(request.session, proname)
    sampledao.getAllSample(id._1, id._2).flatMap { x =>
      Thread.sleep(2000)
      sampledao.getAllSample(id._1, id._2).map { y =>
        val s = x.diff(y)
        val valid = if (s.size != 0) {
          "true"
        } else {
          "false"
        }
        Ok(Json.obj("valid" -> valid))
      }
    }
  }

  def getAllSample(proname: String) = Action { implicit request =>
    val json = dealWithSample(proname, request.session)
    Ok(Json.toJson(json))
  }

  def dealWithSample(proname: String, session: Session) = {
    val id = getUserIdAndProId(session, proname)
    val samples = Await.result(sampledao.getAllSample(id._1, id._2), Duration.Inf)
    val json = samples.sortBy(_.id).reverse.map { x =>
      val sample = x.sample
      val inputtype = x.inputType
      val date = x.createdata.toLocalDate
      val state = if (x.state == 0) {
        "正在运行 <img src='/assets/images/timg.gif'  style='width: 20px; height: 20px;'><input class='state' value='" + x.state + "'>"
      } else if (x.state == 1) {
        "成功<input class='state' value='" + x.state + "'>"
      } else {
        "失败<input class='state' value='" + x.state + "'>"
      }
      val results = if (x.state == 1) {
        if(x.inputType == "PE"){
        s"""
           |<a class="fastq" href="/transcriptome/sample/downloadPE?id=${x.id}&code=1" title="原始数据"><b>${x.sample}</b><b>_1.fastq</b></a>
           |<a class="fastq" target="_blank" href="/transcriptome/sample/openHtml?id=${x.id}&code=1" title="查看原始数据统计报告"><i class="fa fa-eye"></i></a>,
           |<a class="fastq" href="/transcriptome/sample/downloadPE?id=${x.id}&code=2" title="原始数据"><b>${x.sample}</b><b>_2.fastq</b></a>
           |<a class="fastq" target="_blank" href="/transcriptome/sample/openHtml?id=${x.id}&code=2" title="查看原始数据统计报告"><i class="fa fa-eye"></i></a>
           """.stripMargin
        }else{
          s"""
             |<a class="fastq" href="/transcriptome/sample/downloadSE?id=${x.id}&code=1" title="原始数据"><b>${x.sample}</b><b>.fastq</b></a>
             |<a class="fastq" target="_blank" href="/transcriptome/sample/openHtml?id=${x.id}&code=1" title="查看原始数据统计报告"><i class="fa fa-eye"></i></a>
           """.stripMargin
        }

      } else {
        ""
      }
      val operation = if (x.state == 1) {
        s"""
           |  <button class="update" onclick="updateSample(this)" value="${x.sample}" id="${x.id}" title="修改样品名"><i class="fa fa-pencil"></i></button>
           |  <button class="update" onclick="restart(this)" value="${x.id}" title="重新运行质控"><i class="fa fa-repeat"></i></button>
           |  <button class="update" onclick="openLog(this)" value="${x.id}" title="查看日志"><i class="fa fa-file-text"></i></button>
           |  <button class="delete" onclick="openDelete(this)" value="${x.sample}" id="${x.id}" title="删除样品"><i class="fa fa-trash"></i></button>
           """.stripMargin
      } else if (x.state == 2) {
        s"""<button class="delete" onclick="openDelete(this)" value="${x.sample}" id="${x.id}" title="删除样品"><i class="fa fa-trash"></i></button>
           | <button class="update" onclick="openLog(this)" value="${x.id}" title="查看日志"><i class="fa fa-file-text"></i></button>
         """.stripMargin
      } else {
        s"""<button class="delete" onclick="openDelete(this)" value="${x.sample}" id="${x.id}" title="删除样品"><i class="fa fa-trash"></i></button>"""
      }
      Json.obj("sample" -> sample, "input_type" -> inputtype, "state" -> state, "createdate" -> date, "results" -> results, "operation" -> operation)
    }
    json

  }



  def openHtml(id: Int, code: Int) = Action { implicit request =>
    val row = Await.result(sampledao.getAllById(id), Duration.Inf)
    val path = Utils.outPath(row.accountid, row.projectid, id)
    val html = if (row.inputType == "PE") {
      if (code == 1) {
        FileUtils.readLines(new File(path, "raw.data_1_fastqc.html")).asScala
      } else {
        FileUtils.readLines(new File(path, "raw.data_2_fastqc.html")).asScala
      }
    } else {
      FileUtils.readLines(new File(path, "raw.data_fastqc.html")).asScala
    }
    Ok(html.mkString("\n")).as(HTML)
  }

  def getAllPESample(proname:String) = Action.async{implicit request=>
    val ses = getUserIdAndProId(request.session,proname)
    sampledao.getSampleByPosition(ses._1,ses._2,1,"PE").map{x=>
      val sample = x.map(_.sample)
      Ok(Json.toJson(sample))
    }
  }

  def getAllSESample(proname:String) = Action.async{implicit request=>
    val ses = getUserIdAndProId(request.session,proname)
    sampledao.getSampleByPosition(ses._1,ses._2,1,"SE").map{x=>
      val sample = x.map(_.sample)
      Ok(Json.toJson(sample))
    }
  }
}
