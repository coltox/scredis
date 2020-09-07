package scredis.protocol.requests

import scredis.protocol._

object ConnectionRequests {
  
  object Auth extends Command("AUTH")
  object Echo extends Command("ECHO")
  object Ping extends ZeroArgCommand("PING")
  object Quit extends ZeroArgCommand("QUIT")
  object Select extends Command("SELECT")
  
  case class Auth(password: String, username: Option[String])
      extends Request[Unit](Auth, authParams(username, password): _*) {
    override def decode = {  
      case SimpleStringResponse(value) => ()
    }
  }
  
  case class Echo(message: String) extends Request[String](Echo, message) {
    override def decode = {  
      case b: BulkStringResponse => b.flattened[String]
    }
  }
  
  case class Ping() extends Request[String](Ping) {
    override def decode = {  
      case SimpleStringResponse(value) => value
    }
  }
  
  case class Quit() extends Request[Unit](Quit) {
    override def decode = {  
      case SimpleStringResponse(value) => ()
    }
  }
  
  case class Select(database: Int) extends Request[Unit](Select, database) {
    override def decode = {  
      case SimpleStringResponse(value) => ()
    }
  }

  private def authParams(username: Option[String], password: String): List[String] = {
    List(username, Some(password)).flatten
  }
}