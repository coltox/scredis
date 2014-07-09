package scredis.commands

import org.scalatest._
import org.scalatest.concurrent._

import scredis._
import scredis.protocol.requests.ServerRequests._
import scredis.exceptions._
import scredis.tags._
import scredis.util.TestUtils._

import scala.concurrent.duration._

class ServerCommandsSpec extends WordSpec
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers
  with Inside
  with ScalaFutures {
  
  private val client = Client()
  private val client1 = Client(port = 6380, password = Some("foobar"))
  private val client2 = Client(port = 6380, password = Some("foobar"))
  private val client3 = Client(port = 6380, password = Some("foobar"))
  
  BGRewriteAOF.name should {
    "succeed" taggedAs (V100) in {
      client.bgRewriteAOF().futureValue should be (())
    }
  }

  BGSave.name when {
    "not already running" should {
      "succeed" taggedAs (V100) in {
        client.bgSave().futureValue should be (())
      }
    }
  }

  ClientGetName.name when {
    "client has no name" should {
      "return None" taggedAs (V269) in {
        client.clientGetName().futureValue should be (empty)
      }
    }
    "client has a name" should {
      "return client's name" taggedAs (V269) in {
        client.clientSetName("foo")
        client.clientGetName().futureValue should contain ("foo")
      }
    }
  }
  
  ClientKill.name when {
    "providing invalid ip and port" should {
      "return an error" taggedAs (V240) in {
        a [RedisErrorResponseException] should be thrownBy {
          client.clientKill("lol", -1).futureValue
        }
      }
    }
    "killing a non-existing client" should {
      "return an error" taggedAs (V240) in {
        a [RedisErrorResponseException] should be thrownBy { 
          client.clientKill("110.44.56.127", 53655).futureValue
        }
      }
    }
    "killing a connected client" should {
      "succeed" taggedAs (V240) in {
        client2.ping().futureValue should be ("PONG")
        val client2Data = client1.clientList().!.reduceLeft((c1, c2) => {
          if (c1("age").toInt < c2("age").toInt) c1
          else c2
        })
        val (ip, port) = {
          val split = client2Data("addr").split(":")
          (split(0), split(1).toInt)
        }
        client1.clientKill(ip, port)
        client1.clientList().futureValue should have size (1)
      }
    }
    "killing self" should {
      "succeed" taggedAs (V240) in {
        val clientData = client1.clientList().futureValue.head
        val (ip, port) = {
          val split = clientData("addr").split(":")
          (split(0), split(1).toInt)
        }
        client1.clientKill(ip, port)
      }
    }
  }
  
  s"${ClientKill.name}-2.8.12" when {
    "killing by addresses" should {
      "succeed" taggedAs (V2812) in {
        client2.clientSetName("client2").!
        client3.clientSetName("client3").!
        val clients = client1.clientList().!
        val client2Addr = clients.filter { map =>
          map("name") == "client2"
        }.head("addr")
        val client3Addr = clients.filter { map =>
          map("name") == "client3"
        }.head("addr")
        val (ip2, port2) = {
          val split = client2Addr.split(":")
          (split(0), split(1).toInt)
        }
        val (ip3, port3) = {
          val split = client3Addr.split(":")
          (split(0), split(1).toInt)
        }
        client1.clientKillWithFilters(
          addrs = Seq((ip2, port2), (ip3, port3))
        ).futureValue should be (2)
      }
    }
    "killing by ids" should {
      "succeed" taggedAs (V2812) in {
        client2.clientSetName("client2").!
        client3.clientSetName("client3").!
        val clients = client1.clientList().!
        val client2Id = clients.filter { map =>
          map("name") == "client2"
        }.head("id").toInt
        val client3Id = clients.filter { map =>
          map("name") == "client3"
        }.head("id").toInt
        client1.clientKillWithFilters(
          ids = Seq(client2Id, client3Id)
        ).futureValue should be (2)
      }
    }
    "killing by type" should {
      Given("that skipMe is true")
      "kill all clients except self" taggedAs (V2812) in {
        client2.clientSetName("client2").!
        client3.clientSetName("client3").!
        client1.clientKillWithFilters(
          types = Seq(ClientType.Normal), skipMe = true
        ).futureValue should be (2)
      }
      Given("that skipMe is false")
      "kill all clients including self" taggedAs (V2812) in {
        client2.clientSetName("client2").!
        client3.clientSetName("client3").!
        client1.clientKillWithFilters(
          types = Seq(ClientType.Normal), skipMe = false
        ) should be (3)
      }
    }
  }

  ClientList.name when {
    "3 clients are connected" should {
      "list the 3 clients" taggedAs (V240) in {
        client2.clientSetName("client2").!
        client3.clientSetName("client3").!
        client1.clientList().futureValue should have size (3)
      }
    }
    "2 clients are connected" should {
      "list the 2 clients" taggedAs (V240) in {
        client3.quit().!
        client1.clientList().futureValue should have size (2)
      }
    }
    "no other clients are connected" should {
      "list the calling client" taggedAs (V240) in {
        client2.quit().!
        client1.clientList().futureValue should have size (1)
      }
    }
  }
  
  /* FIXME: add when redis 3.0.0 is out
  ClientPause.name should {
    "succeed" taggedAs (V2950) in {
      client.clientPause(500).futureValue should be (())
    }
  }*/
  
  ClientSetName.name when {
    "name is not empty" should {
      "set the specified name" taggedAs (V269) in {
        client.clientSetName("bar")
        client.clientGetName().futureValue should contain ("bar")
      }
    }
    "name is the empty string" should {
      "unset client's name" taggedAs (V269) in {
        client.clientSetName("")
        client.clientGetName().futureValue should be (empty)
      }
    }
  }

  ConfigGet.name when {
    "providing an non-existent key" should {
      "return None" taggedAs (V200) in {
        client.configGet("thiskeywillneverexist").futureValue should be (empty)
      }
    }
    "not providing any pattern" should {
      "use * and return all" taggedAs (V200) in {
        client.configGet().futureValue should not be (empty)
      }
    }
    "providing a valid key that is empty" should {
      "return a None value" taggedAs (V200) in {
        client.configGet("requirepass").!("requirepass") should be (empty)
      }
    }
    "providing a valid key that is not empty" should {
      "return the key's value" taggedAs (V200) in {
        client.configGet("port").!("port") should be ("6379")
      }
    }
  }
  
  ConfigResetStat.name should {
    "reset stats" taggedAs (V200) in {
      client.info().!("total_commands_processed").toInt should be > (1)
      client.configResetStat().futureValue should be (())
      client.info().!("total_commands_processed").toInt should be (1)
    }
  }
  
  ConfigRewrite.name should {
    "succeed" taggedAs (V280) in {
      client.configRewrite().futureValue should be (())
    }
  }

  ConfigSet.name when {
    "providing a non-existent key" should {
      "return an error" taggedAs (V200) in {
        a [RedisErrorResponseException] should be thrownBy { 
          client.configSet("thiskeywillneverexist", "foo").futureValue
        }
      }
    }
    "changing the password" should {
      "succeed" taggedAs (V200) in {
        client.configSet("requirepass", "guessit").!
        a [RedisErrorResponseException] should be thrownBy {
          client.ping().futureValue
        }
        client.auth("guessit").futureValue should be (())
        client.configGet("requirepass").!("requirepass") should be ("guessit")
        client.configSet("requirepass", "") should be (())
        client.configGet("requirepass").!("requirepass") should be (empty)
        client.auth("").futureValue should be (())
      }
    }
  }

  DBSize.name should {
    "return the size of the current database" taggedAs (V100) in {
      client.select(0).futureValue should be (())
      client.flushDB().futureValue should be (())
      client.set("SOMEKEY", "SOMEVALUE")
      client.dbSize().futureValue should be (1)
      client.select(1).futureValue should be (())
      client.dbSize().futureValue should be (0)
      client.set("SOMEKEY", "SOMEVALUE")
      client.set("SOMEKEY2", "SOMEVALUE2")
      client.dbSize().futureValue should be (2)
    }
  }
  
  FlushAll.name should {
    "flush all databases" taggedAs (V100) in {
      client.select(0).futureValue should be (())
      client.set("SOMEKEY", "SOMEVALUE")
      client.select(1).futureValue should be (())
      client.set("SOMEKEY", "SOMEVALUE")
      client.flushAll().futureValue should be (())
      client.dbSize().futureValue should be (0)
      client.select(0).futureValue should be (())
      client.dbSize().futureValue should be (0)
    }
  }

  FlushDB.name should {
    "flush the current database only" taggedAs (V100) in {
      client.select(0).futureValue should be (())
      client.set("SOMEKEY", "SOMEVALUE")
      client.select(1).futureValue should be (())
      client.set("SOMEKEY", "SOMEVALUE")
      client.dbSize().futureValue should be (1)
      client.flushDB().futureValue should be (())
      client.dbSize().futureValue should be (0)
      client.select(0).futureValue should be (())
      client.dbSize().futureValue should be (1)
    }
  }

  Info.name when {
    "not provided with any section" should {
      "return the default section" taggedAs (V100) in {
        val map = client.info().!
        map.contains("redis_version") should be (true)
        map.contains("connected_clients") should be (true)
        map.contains("used_memory") should be (true)
        map.contains("loading") should be (true)
        map.contains("total_connections_received") should be (true)
        map.contains("role") should be (true)
        map.contains("used_cpu_sys") should be (true)
      }
    }
    "provided with a non-existent section" should {
      "return an empty string/map" taggedAs (V260) in {
        client.info("YOU BET").futureValue should be (empty)
      }
    }
    "provided with a valid section" should {
      "only return the selected section" taggedAs (V260) in {
        val map = client.info("server").!
        map.contains("redis_version") should be (true)
        map.contains("connected_clients") should be (false)
      }
    }
  }

  LastSave.name should {
    "return the UNIX timestamp of the last database save" taggedAs (V100) in {
      client.lastSave().futureValue shouldBe > (0)
    }
  }
  
  scredis.protocol.requests.ServerRequests.Role.name when {
    "querying a master database" should {
      "return the master role" taggedAs (V2812) in {
        client1.slaveOf("127.0.0.1", 6379).futureValue should be (())
        val role = client.role().!
        role shouldBe a [scredis.Role.Master]
        inside(role) {
          case scredis.Role.Master(offset, slaves) => inside(slaves) {
            case scredis.Role.SlaveInfo(ip, port, offset) :: Nil => {
              ip should be ("127.0.0.1")
              port should be (6380)
            }
          }
        }
      }
    }
    "querying a slave database" should {
      "return the slave role" taggedAs (V2812) in {
        val role = client1.role().!
        role shouldBe a [scredis.Role.Slave]
        inside(role) {
          case scredis.Role.Slave(masterIp, masterPort, state, offset) => {
            masterIp should be ("127.0.0.1")
            masterPort should be (6379)
          }
        }
        client1.slaveOfNoOne() should be (())
        client1.role().futureValue shouldBe a [scredis.Role.Master]
      }
    }
  }

  Save.name should {
    "save the database" taggedAs (V100) in {
      client.save().futureValue should be (())
    }
  }
  
  SlowLogGet.name when {
    "count is not specified" should {
      "return all slowlog entries" taggedAs (V2212) in {
        val entries = client.slowLogGet().!
        entries.size shouldBe >= (0)
      }
    }
    "count is specified" should {
      "return at most count slowlog entries" taggedAs (V2212) in {
        val entries = client.slowLogGet(countOpt = Some(3)).!
        entries.size shouldBe >= (0)
        entries.size shouldBe <= (3)
      }
    }
  }
  
  SlowLogLen.name should {
    "return the size of the slowlog" taggedAs (V2212) in {
      client.slowLogLen().futureValue shouldBe >= (0)
    }
  }
  
  SlowLogReset.name should {
    "succeed" taggedAs (V2212) in {
      client.slowLogReset().futureValue should be (())
    }
  }

  Time.name should {
    "return the current server UNIX timestamp with microseconds" taggedAs (V260) in {
      client.time().futureValue shouldBe > (0)
    }
  }

  override def afterAll(): Unit = {
    client.flushAll().!
    client.quit().!
    client1.flushAll().!
    client1.quit().!
    client2.quit().!
    client3.quit().!
  }

}
