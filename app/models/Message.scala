package models
import java.sql.Timestamp
case class Message (id: Long, theme: String, message: String, writer: String, created: Timestamp){

}