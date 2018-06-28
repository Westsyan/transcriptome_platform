package utils

import java.io.File

import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._

object dealFasta {

  def fqToFa(fastq:String,fasta:String) :Unit = {
    val fqFile = FileUtils.readLines(new File(fastq)).asScala
    val fq = fqFile.map(_.replaceAll("@",">")).grouped(4).map(_.take(2).mkString("\n")).mkString("\n")
    FileUtils.writeStringToFile(new File(fasta),fq)
  }

  def getValidFasta(path:String,outPath:String) : Unit ={
    val fasta = FileUtils.readLines(new File(path)).asScala
    val all = fasta.mkString("\t").split(">")
    val maxSeqs = all.map{x=>
      val head = x.split("\t").head.split(" ")
      val seq = head.head.split("_").last
      if(seq == "i1"){
        x
      }else{
        "null"
      }
    }.distinct.diff(Array("null"))

    val validSeqs =maxSeqs.map{x=>
      val gene = ">" + x.split("\t").head.split(" ").head.split("_").dropRight(1).mkString("_")
      val body = x.split("\t").drop(1)
      val seq =  gene +: body
      seq.mkString("\n")
    }.mkString("\n").split("\n").toBuffer
    FileUtils.writeLines(new File(outPath),validSeqs.asJava)

  }

}
