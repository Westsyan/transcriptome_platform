package models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.jdbc.MySQLProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import com.github.tototoshi.slick.MySQLJodaSupport._
  import org.joda.time.DateTime
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Project.schema ++ Sample.schema ++ Task.schema ++ User.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Project
   *  @param id Database column id SqlType(INT), AutoInc
   *  @param accountid Database column accountid SqlType(INT)
   *  @param projectname Database column projectname SqlType(VARCHAR), Length(255,true)
   *  @param description Database column description SqlType(VARCHAR), Length(255,true)
   *  @param createdata Database column createdata SqlType(DATETIME)
   *  @param samcount Database column samcount SqlType(INT) */
  final case class ProjectRow(id: Int, accountid: Int, projectname: String, description: String, createdata: DateTime, samcount: Int)
  /** GetResult implicit for fetching ProjectRow objects using plain SQL queries */
  implicit def GetResultProjectRow(implicit e0: GR[Int], e1: GR[String], e2: GR[DateTime]): GR[ProjectRow] = GR{
    prs => import prs._
    ProjectRow.tupled((<<[Int], <<[Int], <<[String], <<[String], <<[DateTime], <<[Int]))
  }
  /** Table description of table project. Objects of this class serve as prototypes for rows in queries. */
  class Project(_tableTag: Tag) extends profile.api.Table[ProjectRow](_tableTag, Some("transcriptome_platform"), "project") {
    def * = (id, accountid, projectname, description, createdata, samcount) <> (ProjectRow.tupled, ProjectRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(accountid), Rep.Some(projectname), Rep.Some(description), Rep.Some(createdata), Rep.Some(samcount)).shaped.<>({r=>import r._; _1.map(_=> ProjectRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc */
    val id: Rep[Int] = column[Int]("id", O.AutoInc)
    /** Database column accountid SqlType(INT) */
    val accountid: Rep[Int] = column[Int]("accountid")
    /** Database column projectname SqlType(VARCHAR), Length(255,true) */
    val projectname: Rep[String] = column[String]("projectname", O.Length(255,varying=true))
    /** Database column description SqlType(VARCHAR), Length(255,true) */
    val description: Rep[String] = column[String]("description", O.Length(255,varying=true))
    /** Database column createdata SqlType(DATETIME) */
    val createdata: Rep[DateTime] = column[DateTime]("createdata")
    /** Database column samcount SqlType(INT) */
    val samcount: Rep[Int] = column[Int]("samcount")

    /** Primary key of Project (database name project_PK) */
    val pk = primaryKey("project_PK", (id, accountid))
  }
  /** Collection-like TableQuery object for table Project */
  lazy val Project = new TableQuery(tag => new Project(tag))

  /** Entity class storing rows of table Sample
   *  @param id Database column id SqlType(INT), AutoInc
   *  @param sample Database column sample SqlType(VARCHAR), Length(255,true)
   *  @param accountid Database column accountid SqlType(INT)
   *  @param projectid Database column projectid SqlType(INT)
   *  @param createdata Database column createdata SqlType(DATETIME)
   *  @param inputType Database column input_type SqlType(VARCHAR), Length(255,true)
   *  @param state Database column state SqlType(INT) */
  final case class SampleRow(id: Int, sample: String, accountid: Int, projectid: Int, createdata: DateTime, inputType: String, state: Int)
  /** GetResult implicit for fetching SampleRow objects using plain SQL queries */
  implicit def GetResultSampleRow(implicit e0: GR[Int], e1: GR[String], e2: GR[DateTime]): GR[SampleRow] = GR{
    prs => import prs._
    SampleRow.tupled((<<[Int], <<[String], <<[Int], <<[Int], <<[DateTime], <<[String], <<[Int]))
  }
  /** Table description of table sample. Objects of this class serve as prototypes for rows in queries. */
  class Sample(_tableTag: Tag) extends profile.api.Table[SampleRow](_tableTag, Some("transcriptome_platform"), "sample") {
    def * = (id, sample, accountid, projectid, createdata, inputType, state) <> (SampleRow.tupled, SampleRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(sample), Rep.Some(accountid), Rep.Some(projectid), Rep.Some(createdata), Rep.Some(inputType), Rep.Some(state)).shaped.<>({r=>import r._; _1.map(_=> SampleRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc */
    val id: Rep[Int] = column[Int]("id", O.AutoInc)
    /** Database column sample SqlType(VARCHAR), Length(255,true) */
    val sample: Rep[String] = column[String]("sample", O.Length(255,varying=true))
    /** Database column accountid SqlType(INT) */
    val accountid: Rep[Int] = column[Int]("accountid")
    /** Database column projectid SqlType(INT) */
    val projectid: Rep[Int] = column[Int]("projectid")
    /** Database column createdata SqlType(DATETIME) */
    val createdata: Rep[DateTime] = column[DateTime]("createdata")
    /** Database column input_type SqlType(VARCHAR), Length(255,true) */
    val inputType: Rep[String] = column[String]("input_type", O.Length(255,varying=true))
    /** Database column state SqlType(INT) */
    val state: Rep[Int] = column[Int]("state")

    /** Primary key of Sample (database name sample_PK) */
    val pk = primaryKey("sample_PK", (id, accountid))
  }
  /** Collection-like TableQuery object for table Sample */
  lazy val Sample = new TableQuery(tag => new Sample(tag))

  /** Entity class storing rows of table Task
   *  @param id Database column id SqlType(INT), AutoInc
   *  @param taskname Database column taskname SqlType(VARCHAR), Length(255,true)
   *  @param accountid Database column accountid SqlType(INT)
   *  @param projectid Database column projectid SqlType(INT)
   *  @param createdata Database column createdata SqlType(DATETIME)
   *  @param state Database column state SqlType(INT)
   *  @param geneSeq Database column gene_seq SqlType(INT)
   *  @param fastaSeq Database column fasta_seq SqlType(INT) */
  final case class TaskRow(id: Int, taskname: String, accountid: Int, projectid: Int, createdata: DateTime, state: Int, geneSeq: Int, fastaSeq: Int)
  /** GetResult implicit for fetching TaskRow objects using plain SQL queries */
  implicit def GetResultTaskRow(implicit e0: GR[Int], e1: GR[String], e2: GR[DateTime]): GR[TaskRow] = GR{
    prs => import prs._
    TaskRow.tupled((<<[Int], <<[String], <<[Int], <<[Int], <<[DateTime], <<[Int], <<[Int], <<[Int]))
  }
  /** Table description of table task. Objects of this class serve as prototypes for rows in queries. */
  class Task(_tableTag: Tag) extends profile.api.Table[TaskRow](_tableTag, Some("transcriptome_platform"), "task") {
    def * = (id, taskname, accountid, projectid, createdata, state, geneSeq, fastaSeq) <> (TaskRow.tupled, TaskRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(taskname), Rep.Some(accountid), Rep.Some(projectid), Rep.Some(createdata), Rep.Some(state), Rep.Some(geneSeq), Rep.Some(fastaSeq)).shaped.<>({r=>import r._; _1.map(_=> TaskRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc */
    val id: Rep[Int] = column[Int]("id", O.AutoInc)
    /** Database column taskname SqlType(VARCHAR), Length(255,true) */
    val taskname: Rep[String] = column[String]("taskname", O.Length(255,varying=true))
    /** Database column accountid SqlType(INT) */
    val accountid: Rep[Int] = column[Int]("accountid")
    /** Database column projectid SqlType(INT) */
    val projectid: Rep[Int] = column[Int]("projectid")
    /** Database column createdata SqlType(DATETIME) */
    val createdata: Rep[DateTime] = column[DateTime]("createdata")
    /** Database column state SqlType(INT) */
    val state: Rep[Int] = column[Int]("state")
    /** Database column gene_seq SqlType(INT) */
    val geneSeq: Rep[Int] = column[Int]("gene_seq")
    /** Database column fasta_seq SqlType(INT) */
    val fastaSeq: Rep[Int] = column[Int]("fasta_seq")

    /** Primary key of Task (database name task_PK) */
    val pk = primaryKey("task_PK", (id, accountid))
  }
  /** Collection-like TableQuery object for table Task */
  lazy val Task = new TableQuery(tag => new Task(tag))

  /** Entity class storing rows of table User
   *  @param id Database column id SqlType(INT), AutoInc
   *  @param account Database column account SqlType(VARCHAR), Length(255,true)
   *  @param password Database column password SqlType(VARCHAR), Length(255,true) */
  final case class UserRow(id: Int, account: String, password: String)
  /** GetResult implicit for fetching UserRow objects using plain SQL queries */
  implicit def GetResultUserRow(implicit e0: GR[Int], e1: GR[String]): GR[UserRow] = GR{
    prs => import prs._
    UserRow.tupled((<<[Int], <<[String], <<[String]))
  }
  /** Table description of table user. Objects of this class serve as prototypes for rows in queries. */
  class User(_tableTag: Tag) extends profile.api.Table[UserRow](_tableTag, Some("transcriptome_platform"), "user") {
    def * = (id, account, password) <> (UserRow.tupled, UserRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(account), Rep.Some(password)).shaped.<>({r=>import r._; _1.map(_=> UserRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc */
    val id: Rep[Int] = column[Int]("id", O.AutoInc)
    /** Database column account SqlType(VARCHAR), Length(255,true) */
    val account: Rep[String] = column[String]("account", O.Length(255,varying=true))
    /** Database column password SqlType(VARCHAR), Length(255,true) */
    val password: Rep[String] = column[String]("password", O.Length(255,varying=true))

    /** Primary key of User (database name user_PK) */
    val pk = primaryKey("user_PK", (id, account))
  }
  /** Collection-like TableQuery object for table User */
  lazy val User = new TableQuery(tag => new User(tag))
}
