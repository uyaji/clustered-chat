package daos
import javax.inject.Inject
import scala.concurrent.Future
import play.api.db.slick.DatabaseConfigProvider
import play.api.db.slick.HasDatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import models.Message
import java.sql.Timestamp
class MessageDao @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile]{
  import driver.api._
  
  private val Messages = TableQuery[MessagesTable]
  
  def all(): Future[Seq[Message]] = db.run(Messages.result)
  def insert(message: Message): Future[Unit] = db.run(Messages += message).map {_ => ()}
  def findById(id: Long): Future[Message] = db.run(Messages.filter(_.id === id).result.head) 
  def findByTheme(theme: String): Future[Seq[Message]] = db.run(Messages.filter(_.theme === theme).sortBy(row => row.id.asc).result)
  
  private class MessagesTable(tag: Tag) extends Table[Message](tag, "messages") {
    def id =column[Long]("ID", O.PrimaryKey)
    def theme = column[String]("theme")
    def message = column[String]("message")
    def writer = column[String]("writer")
    def created = column[Timestamp]("created")
    
    def * = (id, theme, message, writer, created)<>(Message.tupled, Message.unapply)
  }
}