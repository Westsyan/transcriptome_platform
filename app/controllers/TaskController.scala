package controllers

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

import dao._
import models.Tables._
import org.apache.commons.io.FileUtils
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc._
import utils.{ExecCommand, Utils, dealFasta}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TaskController @Inject()(admindao: adminDao, projectdao: projectDao, sampledao: sampleDao, taskdao: taskDao) extends Controller {


  def toTaskPage(proname: String): Action[AnyContent] = Action { implicit request =>
    val userId = request.session.get("id").head.toInt
    val allName = Await.result(projectdao.getAllProject(userId), Duration.Inf)
    Ok(views.html.task.taskPage(allName, proname,request.domain))
  }


  def taskPage(proname: String) = Action { implicit request =>
    val ses = getUserIdAndProId(request.session, proname)
    val allName = Await.result(projectdao.getAllProject(ses._1), Duration.Inf)
    Ok(views.html.task.taskData(allName, proname,request.domain))
  }

  case class taskData(proname: String, taskname: String, paired_or_single: String, sample: Option[Seq[String]],
                      sample2:Option[Seq[String]], library_type: String, norm: String, min_contig_length: Int,
                      min_kmer_cov: Int, est_method: String, aln_method: String, salmon_idx_type: String)

  val taskForm = Form(
    mapping(
      "proname" -> text,
      "taskname" -> text,
      "paired_or_single" -> text,
      "sample" -> optional(seq(text)),
      "sample2" -> optional(seq(text)),
      "library_type" -> text,
      "norm" -> text,
      "min_contig_length" -> number,
      "min_kmer_cov" -> number,
      "est_method" -> text,
      "aln_method" -> text,
      "salmon_idx_type" -> text
    )(taskData.apply)(taskData.unapply)
  )

  def saveDeploy = Action { implicit request =>
    val data = taskForm.bindFromRequest.get
    val proname = data.proname
    val taskname = data.taskname
    val ses = getUserIdAndProId(request.session, proname)
    val date = Utils.date
    val row = TaskRow(0, taskname, ses._1, ses._2, date, 0 , 0 , 0)
    Await.result(taskdao.addTaskInfo(Seq(row)), Duration.Inf)

    val run = Future {
      val task = Await.result(taskdao.getAllByPosition(ses._1, ses._2, taskname), Duration.Inf)
      val taskpath = Utils.taskPath(ses._1, ses._2, task.id)
      new File(taskpath).mkdirs()
      val method = data.paired_or_single
      val strand = data.est_method

      val (iso,genes) = if(strand == "RSEM"){
        ("RSEM.isoforms.results","RSEM.genes.results")
      }else if(strand == "eXpress"){
        ("results.xprs","results.xprs.genes")
      }else if(strand == "salmon"){
        ("quant.sf","quant.sf.genes")
      }else{
        ("abundance.tsv","abundance.tsv.genes")
      }

      val sample = if(method == "paired"){data.sample.head}else{data.sample2.head}

      val cols = sample.map { x =>
        val sampleRow = Await.result(sampledao.getByPosition(ses._1, ses._2, x), Duration.Inf)
        val samplePath = Utils.outPath(ses._1, ses._2, sampleRow.id)
        val col = if (method == "paired") {
          sampleRow.sample + "\t" + sampleRow.sample + "\t" + samplePath + "/r1_paired_out.fastq" + "\t" + samplePath + "/r2_paired_out.fastq"
        } else {
          sampleRow.sample + "\t" + sampleRow.sample + "\t" + samplePath + "/raw_se_out.fastq"
        }
        val isopath = taskpath + "/tmp/" + sampleRow.sample + "/" + iso
        val genespath = taskpath + "/tmp/" + sampleRow.sample + "/" + genes
        (col,isopath,genespath)
      }

      val matrix = cols.map(_._1)
      val isoform = cols.map(_._2)
      val gene = cols.map(_._3)
      val sampleFile = new File(taskpath, "sample.txt")
      FileUtils.writeLines(sampleFile, matrix.asJava)
      val libType = if(method == "paired"){"RF"}else{data.library_type}

      val deploy = mutable.Buffer(proname, taskname, sampleFile.getAbsolutePath, method,
        libType, data.norm, data.min_contig_length, data.min_kmer_cov, data.est_method, data.aln_method,
        data.salmon_idx_type,isoform.mkString(" "),gene.mkString(" "),sample.mkString(","),sample.mkString(","))
      FileUtils.writeLines(new File(taskpath, "/deploy.txt"), deploy.asJava)
      runCmd(task.id)
    }

    val json = Json.obj("valid" -> "true")
    Ok(Json.toJson(json))
  }

  def cmd1(path: String, deploy: mutable.Buffer[String]) : String = {
    val command = s"${Utils.toolPath}/trinityrnaseq-Trinity-v2.5.1/Trinity --seqType fq --max_memory 100G --samples_file " +
      s"${path}/sample.txt  --output ${path}/trinity_tmp ${deploy(5)} --min_contig_length " +
      s"${deploy(6)} --min_kmer_cov ${deploy(7)} --CPU 6 --no_version_check"
    command
  }

  def cmd2(path: String, deploy: mutable.Buffer[String]) : String = {
    val method = if (deploy(8) == "RSEM" || deploy(8) == "eXpress") {
      s"--est_method ${deploy(8)} --aln_method ${deploy(9)}"
    } else if (deploy(8) == "salmon") {
      s"--est_method salmon --salmon_idx_type ${deploy(10)}"
    } else {
      "--est_method kallisto"
    }
    val command = s"perl ${Utils.toolPath}/trinityrnaseq-Trinity-v2.5.1/util/align_and_estimate_abundance.pl --transcripts " +
      s"${path}/trinity_tmp/Trinity.fasta ${method}  --prep_reference --trinity_mode --samples_file ${path}/sample.txt --seqType fq"
    command
  }

  def cmd3(state: Int,deploy: mutable.Buffer[String]) : String = {
    val (results,name) = if(state == 1){(deploy(11),"isoforms")}else{(deploy(12),"genes")}
    val command = s"perl ${Utils.toolPath}/trinityrnaseq-Trinity-v2.5.1/util/abundance_estimates_to_matrix.pl --est_method ${deploy(8)} " +
      s"--gene_trans_map none  ${results} --name_sample_by_basedir   --cross_sample_norm none --out_prefix ${name}"
    command
  }

  def getUserIdAndProId(session: Session, proname: String): (Int, Int) = {
    val userId = session.get("id").head.toInt
    val proId = Await.result(projectdao.getIdByProjectname(userId, proname), Duration.Inf)
    (userId, proId)
  }

  def getTime = Action { implicit request =>
    val now = new Date()
    val dateFormat = new SimpleDateFormat("yyMMddHHmmss")
    val date = dateFormat.format(now)
    Ok(Json.obj("date" -> date))
  }

  case class checkTasknameData(taskname: String)

  val checkTasknameForm = Form(
    mapping(
      "taskname" -> text
    )(checkTasknameData.apply)(checkTasknameData.unapply)
  )

  def checkName(proname: String) = Action.async { implicit request =>
    val data = checkTasknameForm.bindFromRequest.get
    val ses = getUserIdAndProId(request.session, proname)
    taskdao.getAllByPosi(ses._1, ses._2, data.taskname).map { x =>
      val valid = if (x.size == 0) {
        "true"
      } else {
        "false"
      }
      Ok(Json.obj("valid" -> valid))
    }
  }

  def runCmd(id: Int) = {
    val row = Await.result(taskdao.getById(id), Duration.Inf)
    val userId = row.accountid
    val proId = row.projectid
    val path = Utils.taskPath(userId, proId, id)
    val deploy = FileUtils.readLines(new File(path, "deploy.txt")).asScala

    val command1 = cmd1(path,deploy)
    val command2 = cmd2(path,deploy)
  //  val command3 = cmd3(0,deploy)

    val command = new ExecCommand
    val tmp = path + "/tmp"
    new File(tmp).mkdir()
    command.exect(command1,command2, tmp)
    if (command.isSuccess) {
      fpkm(deploy(12).split(" "),deploy(13).split(","),path,deploy(8))
      dealwithFile(id)
      val log = command.getErrStr
      FileUtils.writeStringToFile(new File(path, "log.txt"), log)
    } else {
      Await.result(taskdao.updateState(id, 2), Duration.Inf)
      val log = command.getErrStr
      if (new File(path).exists()) {
        FileUtils.writeStringToFile(new File(path, "log.txt"), log)
      }
      FileUtils.deleteDirectory(new File(tmp))
    }
  }

  def dealwithFile(id:Int) = {
    val task = Await.result(taskdao.getById(id),Duration.Inf)
    val path = Utils.taskPath(task.accountid,task.projectid,id)

    val fasta = FileUtils.readLines(new File(path + "/trinity_tmp/Trinity.fasta.gene_trans_map")).asScala
    val trinity_size = fasta.size
    val genes_size = fasta.map(_.split("\t").head).distinct.size
    FileUtils.moveFile(new File(path + "/trinity_tmp/Trinity.fasta"),new File(path,"Trinity.fasta"))
    dealFasta.getValidFasta(path + "/Trinity.fasta" , path + "/unigene.fasta")
  //  FileUtils.moveFile(new File(path + "/tmp/genes.counts.matrix"),new File(path,"genes.counts_table.txt"))
   // FileUtils.moveFile(new File(path + "/tmp/genes.TPM.not_cross_norm"),new File(path,"genes.tpm_table.txt"))
    FileUtils.deleteDirectory(new File(path,"tmp"))
    FileUtils.deleteDirectory(new File(path,"trinity_tmp"))
    val row = TaskRow(id,task.taskname,task.accountid,task.projectid,task.createdata,1,genes_size,trinity_size)
    Await.result(taskdao.updateTask(row,id),Duration.Inf)
  }

  def fpkm(paths:Array[String],samples:Array[String],path:String,t:String)= {
    val array =
      if(t == "RSEM"){
        paths.map{x=>
          val file = FileUtils.readLines(new File(x)).asScala
          val fpkm = file.map(_.split("\t").last)
          val count = file.map(_.split("\t")(4))
          val ids = file.map(_.split("\t").head)
          (fpkm,count,ids)
        }
      }else{
        paths.map{x=>
          val file = FileUtils.readLines(new File(x)).asScala
          val fpkm = file.map(_.split("\t")(10))
          val count = file.map(_.split("\t")(7))
          val ids = file.map(_.split("\t")(1))
          (fpkm,count,ids)
        }
      }
    val id = array.map(_._3).head
    val count = array.map(_._1)
    val fpk = array.map(_._2)
    var counts = id
    var fpks = id
    for(i <- 0 until  count.size){
      counts = counts.zip(count(i)).map(x=>(x._1+"\t"+x._2))
      fpks = fpks.zip(fpk(i)).map(x=>(x._1+"\t"+x._2))
    }
    val head = "id\t" + samples.mkString("\t")
    counts = head +: counts.drop(1)
    fpks = head +: fpks.drop(1)
    FileUtils.writeLines(new File(path + "/genes.counts_table.txt") ,counts.asJava)
    FileUtils.writeLines(new File(path + "/genes.fpkm_table.txt") ,fpks.asJava)
  }

  def getAllTask(proname: String) = Action { implicit request =>
    val json = dealWithSample(proname, request.session)
    Ok(Json.toJson(json))
  }

  def dealWithSample(proname: String, session: Session) = {
    val id = getUserIdAndProId(session, proname)
    val tasks = Await.result(taskdao.getAllTaskByPosition(id._1, id._2), Duration.Inf)
    val json = tasks.sortBy(_.id).reverse.map { x =>
      val taskname = x.taskname
      val date = x.createdata.toLocalDate
      val state = if (x.state == 0) {
        "正在运行 <img src='/assets/images/timg.gif'  style='width: 20px; height: 20px;'><input class='state' value='" + x.state + "'>"
      } else if (x.state == 1) {
        "成功<input class='state' value='" + x.state + "'>"
      } else {
        "失败<input class='state' value='" + x.state + "'>"
      }
      val gene_seq = if(x.state == 1) {
        x.geneSeq.toString
      }else{
        ""
      }

      val fasta_seq = if(x.state == 1) {
        x.fastaSeq.toString
      }else{
        ""
      }

      val results = if (x.state == 1) {
        s"""
           |<a class="fastq" href="/transcriptome/task/download?id=${x.id}&code=1" title="基因 reads count 矩阵"><b>genes.counts_table.txt</b></a>,&nbsp;
           |<a class="fastq" href="/transcriptome/task/download?id=${x.id}&code=2" title="基因 fpkm 矩阵"><b>genes.fpkm_table.txt</b></a>,&nbsp;
           |<a class="fastq" href="/transcriptome/task/download?id=${x.id}&code=3" title="原始组装结果"><b>isoform.fasta</b></a>,&nbsp;
           |<a class="fastq" href="/transcriptome/task/download?id=${x.id}&code=4" title="unigene组装结果"><b>unigene.fasta</b></a>&nbsp;
           """.stripMargin
      } else {
        ""
      }
      val operation = if (x.state == 1) {
        s"""
           |  <button class="update" onclick="restart(this)" value="${x.id}" title="重新运行RNA组装和定量分析"><i class="fa fa-repeat"></i></button>
           |  <button class="update" onclick="openLog(this)" value="${x.id}" title="查看日志"><i class="fa fa-file-text"></i></button>
           |  <button class="delete" onclick="openDelete(this)" value="${x.taskname}" id="${x.id}" title="删除任务"><i class="fa fa-trash"></i></button>
           """.stripMargin
      } else if (x.state == 2) {
        s"""<button class="delete" onclick="openDelete(this)" value="${x.taskname}" id="${x.id}" title="删除任务"><i class="fa fa-trash"></i></button>
           |<button class="update" onclick="openLog(this)" value="${x.id}" title="查看日志"><i class="fa fa-file-text"></i></button>
         """.stripMargin
      } else {
        s"""<button class="delete" onclick="openDelete(this)" value="${x.taskname}" id="${x.id}" title="删除任务"><i class="fa fa-trash"></i></button>"""
      }
      Json.obj("taskname" -> taskname, "state" -> state, "createdate" -> date, "results" -> results, "operation" -> operation,
              "gene_seq" -> gene_seq, "fasta_seq" -> fasta_seq)
    }
    json

  }

  def deleteTask(id: Int) = Action.async { implicit request =>
    taskdao.getById(id).flatMap { x =>
      taskdao.deleteTask(id).map { y =>
        val run = Future{
          val path = Utils.taskPath(x.accountid, x.projectid, id)
          FileUtils.deleteDirectory(new File(path))
        }
        Ok(Json.toJson("success"))
      }
    }
  }

  def getLog(id: Int) = Action { implicit request =>
    val row = Await.result(taskdao.getById(id), Duration.Inf)
    val path = Utils.taskPath(row.accountid, row.projectid, row.id)
    val log = FileUtils.readLines(new File(path, "log.txt")).asScala
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

  def download(id: Int, code: Int) = Action { implicit request =>
    val row = Await.result(taskdao.getById(id), Duration.Inf)
    val path = Utils.taskPath(row.accountid, row.projectid, id)
    val (file, name) = if (code == 1) {
      (new File(path, "genes.counts_table.txt"), "genes.counts_table.txt")
    } else if (code == 2) {
      (new File(path, "genes.fpkm_table.txt"), "genes.fpkm_table.txt")
    } else if (code == 3) {
      (new File(path, "Trinity.fasta"), "isoform.fasta")
    } else {
      (new File(path, "unigene.fasta"), "unigene.fasta")
    }
    Ok.sendFile(file).withHeaders(
      CACHE_CONTROL -> "max-age=3600",
      CONTENT_DISPOSITION -> ("attachment; filename=" + name),
      CONTENT_TYPE -> "application/x-download"
    )
  }

  case class updateTasknameData(taskId: Int, newtaskname: String)

  val updateTasknameForm = Form(
    mapping(
      "taskId" -> number,
      "newtaskname" -> text
    )(updateTasknameData.apply)(updateTasknameData.unapply)
  )

  def updateTaskname = Action.async { implicit request =>
    val data = updateTasknameForm.bindFromRequest.get
    val id = data.taskId
    val name = data.newtaskname
    taskdao.updateTaskName(id, name).map { x =>
      Ok(Json.obj("valid" -> "true"))
    }
  }

  def getDeploy(id: Int) = Action.async { implicit request =>
    val x = Await.result(taskdao.getById(id), Duration.Inf)
    val path = Utils.taskPath(x.accountid, x.projectid, x.id)
    val deploy = FileUtils.readLines(new File(path, "deploy.txt")).asScala
    val sample = deploy.last.split(",")
    taskdao.getById(id).flatMap { x =>
      val userId = x.accountid
      val proId = x.projectid
      sampledao.checkByPosition(userId, proId, deploy.last).map { y =>
        val (valid, message) = if (sample.size == y.size) {
          ("true", "success")
        } else {
          val validSample = y.map(_.sample)
          val s = sample.diff(validSample)
          ("false", "样品" + s.mkString(",") + "已被删除")
        }

        val json = Json.obj("sample" -> deploy.last,"data_type" -> deploy(3),"taskIds" -> x.id, "library_type" -> deploy(4),
          "norm" -> deploy(5),"min_contig_length" -> deploy(6),"min_kmer_cov"->deploy(7),"est_method" ->deploy(8),
          "aln_method" -> deploy(9), "salmon_idx_type" -> deploy(10), "valid" -> valid, "message" -> message)
        Ok(Json.toJson(json))
      }
    }
  }


  case class resetData(samples: String,data_type:String ,taskIds:Int, library_type: Option[String], norm: String, min_contig_length: Int,
                       min_kmer_cov: Int, est_method: String, aln_method: String, salmon_idx_type: String)

  val resetForm = Form(
    mapping(
      "samples" -> text,
      "data_type" -> text,
      "taskIds" -> number,
      "library_type" -> optional(text),
      "norm" -> text,
      "min_contig_length" -> number,
      "min_kmer_cov" -> number,
      "est_method" -> text,
      "aln_method" -> text,
      "salmon_idx_type" -> text
    )(resetData.apply)(resetData.unapply)
  )

  def resetTask = Action.async { implicit request =>
    val data = resetForm.bindFromRequest.get
    val taskid = data.taskIds
    taskdao.getById(taskid).flatMap { x =>
      val path = Utils.taskPath(x.accountid, x.projectid, x.id)
      val buffer = FileUtils.readLines(new File(path, "deploy.txt")).asScala
      val libType =if(data.data_type == "paired"){"RF"}else{data.library_type.head}

      val b = mutable.Buffer(buffer.head, buffer(1), buffer(2),buffer(3), libType ,data.norm,data.min_contig_length,
        data.min_kmer_cov,data.est_method,data.aln_method,data.salmon_idx_type,buffer(11),buffer(12),buffer(13),buffer.last)
      deleteFile(path)
      FileUtils.writeLines(new File(path, "deploy.txt"), b.asJava)
      taskdao.updateState(x.id, 0).map { y =>
        Ok(Json.obj("valid" -> "true", "id" -> x.id))
      }
    }
  }

  def deleteFile(path:String) :Unit = {
    new File(path,"Trinity.fasta").delete()
    new File(path,"genes.counts_table.txt").delete()
    new File(path,"genes.tpm_table.txt").delete()
    new File(path,"unigene.fasta").delete()
    new File(path,"deploy.txt").delete()
  }


  def runResetCmd(id: Int) = Action.async { implicit request =>
    taskdao.getById(id).map { x =>
      runCmd(x.id)
      Ok(Json.toJson("success"))
    }
  }

  case class checkNewnameData(newtaskname: String)

  val checkNewnameForm = Form(
    mapping(
      "newtaskname" -> text
    )(checkNewnameData.apply)(checkNewnameData.unapply)
  )

  def checkNewname(proname: String) = Action.async { implicit request =>
    val data = checkNewnameForm.bindFromRequest.get
    val ses = getUserIdAndProId(request.session, proname)
    taskdao.getAllByPosi(ses._1, ses._2, data.newtaskname).map { x =>
      val valid = if (x.size == 0) {
        "true"
      } else {
        "false"
      }
      Ok(Json.obj("valid" -> valid))
    }
  }

}


