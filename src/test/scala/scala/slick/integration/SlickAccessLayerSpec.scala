package scala.slick.integration

import org.specs2.mutable._
import play.api.db.DB
import scala.slick.driver.H2Driver
import scala.slick.lifted.DDL
import scala.slick.session._
import org.specs2.execute.Result
import org.specs2.specification.AroundOutside

case class Product(name: String, description: String, id: Option[Long] = None) extends Entity[Product] {
  def withId(id: Long): Product = copy(id = Some(id))
}

trait ProductComponent extends _Component[Product] { self: Profile =>

  import profile.simple._
  
  object Products extends Mapper("product") {

    def name = column[String]("name")
    def description = column[String]("description")

    def * = name ~ description ~ id.? <> (Product, Product.unapply _)

    lazy val existsNameQuery = for {
      name <- Parameters[String] 
    } yield Products.where(_.name === name).map(_.id).exists
    
    def existsName(name: String): Boolean = db.withSession { implicit s: Session =>
      existsNameQuery.first(name)
    }
    
    lazy val findByNameAndDescriptionQuery = for {
      (name, description) <- Parameters[(String, String)]
      p <- Products if p.name === name && p.description === description
    } yield p
    
    def findByNameAndDescription1(name: String, description: String): Option[Product] = db.withSession { implicit s: Session =>
      findByNameAndDescriptionQuery.firstOption(name, description)
    }

    def findByNameAndDescription2(name: String, description: String): Option[Product] = db.withSession { implicit s: Session =>
      findByNameAndDescriptionQuery(name, description).firstOption
    }

  }

}

class DAL extends _DAL with ProductComponent with Profile {

  // trait Profile implementation
  val profile = H2Driver 
  def db = Database.forURL("jdbc:h2:mem:test1", driver = "org.h2.Driver")

  // _DAL.ddl implementation
  lazy val ddl: DDL = Products.ddl
  
  db.withSession { implicit s: Session =>
    import profile.simple._
  	ddl.create
  }

}

class DataAccessLayerSpec extends Specification {

  val DAL = new DAL()
  
  import DAL._
  
  "Slick Integration" should {
    "be testable with in-memory h2 driver" in {
      db.withSession { s: Session =>
        s.metaData.getDatabaseProductName must equalTo("H2")
      }
    }
  }

  "Product model" should {
    
    "crud" in {
      db.withSession { implicit s: Session =>
        
        create
        
        val product = Products.insert(Product("name", "description"))
        product.id must not equalTo (None) // AutoInc id correct
        product.id.map { id =>
          
          Products.findById(id) must equalTo(Some(Product("name", "description", Some(id)))) // product found
          Products.existsName("name") must equalTo(true)
          Products.existsName("new") must equalTo(false)
          
          Products.update(product.copy(name = "new")) must equalTo(1)
          Products.existsName("new") must equalTo(true)
          Products.existsName("name") must equalTo(false)
          
          Products.delete(id) must equalTo(1) // one row deleted 
          Products.findById(id) must equalTo(None) // product not found
          Products.existsName("new") must equalTo(false)
          
        }
        
        drop
        
        success
      }
    }
    
//    "query template with two parameters" in {
//      db.withSession { implicit s: Session =>
//        
//        create
//        
//        val product = Products.insert(Product("name", "description"))
//        
//        Products.findByNameAndDescription1("name", "description") should equalTo(Some(product))
//        Products.findByNameAndDescription1("xxx", "description") should equalTo(None)
//        
//        Products.findByNameAndDescription2("name", "description") should equalTo(Some(product))
//        Products.findByNameAndDescription2("xxx", "description") should equalTo(None)
//        
//        drop
//        
//        success
//      }
//    }
    
  }
  
}
