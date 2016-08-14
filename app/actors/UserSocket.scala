package actors

import actors.UserSocket.{ChatMessage, Message}
import actors.UserSocket.Message.messageReads
import akka.actor.{Actor, ActorLogging, ActorRef, Props, InvalidActorNameException}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Send, Put}
import akka.event.LoggingReceive
import play.api.libs.json.{Writes, JsValue, Json}
import play.twirl.api.HtmlFormat
import scala.xml.Utility
import daos.MessageDao
import java.sql.Timestamp
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object UserSocket{

  def props(userTheme: String, messageDao: MessageDao)(out: ActorRef) = Props(new UserSocket(userTheme, messageDao, out))

  case class Message(msg: String)

  object Message {
    implicit val messageReads = Json.reads[Message]
  }

  case class ChatMessage(user: String, text: String, theme: String)

  object ChatMessage {
    implicit val chatMessageWrites = new Writes[ChatMessage] {
      def writes(chatMessage: ChatMessage): JsValue = {
        Json.obj(
          "type" -> "message",
          "user" -> chatMessage.user,
          "text" -> multiLine(chatMessage.text),
          "theme" -> chatMessage.theme
        )
      }
    }

    private def multiLine(text: String) = {
      HtmlFormat.raw(text).body.replace("\n", "<br/>")
    }
  }
}

class UserSocket(userTheme: String, messageDao: MessageDao, out: ActorRef) extends Actor with ActorLogging {

  
  val user = userTheme.split("/").apply(0)
  val theme = userTheme.split("/").apply(1)

  val mediator = DistributedPubSub(context.system).mediator

  println("****************: path = " + self.path + " *****************")
  mediator ! Subscribe(theme, self)
  
  try {
    context.system.actorOf(MessageRecorder.props(theme, messageDao), "recorder")  
  } catch {
    case aie:InvalidActorNameException =>{}
    case e:Exception =>{e.printStackTrace()}
  }
  
  
  def receive = LoggingReceive {
    case js: JsValue => {
      println("************** receive JSON **************** " + user)
      js.validate[Message](messageReads)
        .map(message => Utility.escape(message.msg))
        .foreach { message => {
         mediator ! Publish(theme, ChatMessage(user, message, theme))
         mediator ! Send(path = "/user/recorder", msg = ChatMessage(user, message, theme), localAffinity = true)
        }
//        .foreach { message => mediator ! Send(path = "/user/" + uid, msg = message, localAffinity = true)} 
        }
    }
    case c:ChatMessage =>{
      println("************** receive message ************* " + user + " / " + self.toString() + " / " + c)
      out ! Json.toJson(c) 
    }
      
  }
}
object MessageRecorder {

  def props(theme: String, messageDao: MessageDao) = Props(new MessageRecorder(theme, messageDao))
  
}

class MessageRecorder(theme: String, messageDao: MessageDao) extends Actor with ActorLogging {
  println("****************: recoder = " + self.path + " *****************")
  DistributedPubSub(context.system).mediator ! Put(self)
//  DistributedPubSub(context.system).mediator ! Subscribe(theme, self)
  
  def receive = {
    case c:String =>{
      println("************* Receive String path ********************* [" + c + "] " + self.toString())
    }
    case cm:ChatMessage  =>{
      messageDao.insert(new models.Message(0L, cm.theme, cm.text, cm.user, new Timestamp(System.currentTimeMillis())))
      println("************** Receive message path ************* [" + cm.text + "]" + self.toString())
    }
  }
}

object MessageReader {

  def props(messageDao: MessageDao)(out: ActorRef) = Props(new MessageReader(messageDao, out))
  
}

class MessageReader(messageDao: MessageDao, out: ActorRef) extends Actor with ActorLogging {
  println("****************: reader = " + self.path + " *****************")
  DistributedPubSub(context.system).mediator ! Put(self)
  
  def receive = {
    case js: JsValue =>{
      println("****************: reader1 = " + js.toString() + " *****************")
      val key =js.toString().split("/").apply(1)
      val result = messageDao.findByTheme(key.substring(0, key.length()-1))
      result.map { messageList => {
        messageList.map { message => {
//          println("writer = " + message.writer + " message = " + message.message + "theme = " + message.theme)
          val msg = ChatMessage(message.writer, message.message, message.theme)
          out ! Json.toJson(msg)
        }}
      }}
    }
      println("****************: reader2 = " + js.toString().split("/").apply(1) + " *****************")
  }
}
