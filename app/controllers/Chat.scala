package controllers

import javax.inject._

import actors.{UserSocket, ChatRoom, MessageReader}
import akka.actor._
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsValue
import play.api.mvc.{Action, Controller, WebSocket}

import scala.concurrent.Future

@Singleton
class Chat @Inject()(val messagesApi: MessagesApi, system: ActorSystem, messageDao: daos.MessageDao) extends Controller with I18nSupport {
  val UserTheme = "userTheme"

  val chatRoom = system.actorOf(Props[ChatRoom], "chat-room")
  println("************* chatRoom path = " + chatRoom.path + " ***********")

//  val nickForm = Form(single("nickname" -> nonEmptyText))
  val nickForm = Form(tuple("nickname" -> nonEmptyText,"groupname" -> nonEmptyText))

  def index = Action { implicit request =>
    request.session.get(UserTheme).map { user =>
      Redirect(routes.Chat.chat()).flashing("info" -> s"Redirected to chat as $user user")
    }.getOrElse(Ok(views.html.index(nickForm)))
  }

  def nickname = Action { implicit request =>
    nickForm.bindFromRequest.fold(
      formWithErrors => {
        BadRequest(views.html.index(formWithErrors))
      },
      userTheme => {
        println("************* group = " + userTheme._2)
        Redirect(routes.Chat.chat())
          .withSession(request.session + (UserTheme -> (userTheme._1 + "/" + userTheme._2)))
      }
    )
  }

  def leave = Action { implicit request =>
    Redirect(routes.Chat.index()).withNewSession.flashing("success" -> "See you soon!")
  }

  def chat = Action { implicit request =>
    request.session.get(UserTheme).map { userTheme => {
//      request.session.get(Group).map { group => {
        println("*********** user = " + userTheme.split("/").apply(0) + " / " +  userTheme.split("/").apply(1) + " ******************")
        Ok(views.html.chat(userTheme))
//        Ok(views.html.chat("uyaji"))
//      }}.getOrElse(Redirect(routes.Chat.index()))   
        }}.getOrElse(Redirect(routes.Chat.index()))
  }

  def socket = WebSocket.tryAcceptWithActor[JsValue, JsValue] { implicit request =>
    Future.successful(request.session.get(UserTheme) match {
      case None => Left(Forbidden)
      case Some(userTheme) => {
        println("*********** uid = " + userTheme + " ******************")
        Right(UserSocket.props(userTheme, messageDao))
      }
    })
  }

  def socketRead = WebSocket.tryAcceptWithActor[JsValue, JsValue] { implicit request =>
    Future.successful(request.session.get(UserTheme) match {
      case None => Left(Forbidden)
      case Some(userTheme) => {
        Right(MessageReader.props(messageDao))
      }
    })
  }
}
