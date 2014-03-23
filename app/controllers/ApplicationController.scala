package controllers

import play.api._
import play.api.mvc._
import com.fasterxml.jackson.databind.node._
import play.db.jpa.JPA
import scala.collection.JavaConverters._

import org.hibernate.criterion.Restrictions
import org.hibernate.criterion.Projections
import org.hibernate.criterion.Order


object ApplicationController extends Controller {

	def index = Action {
		Ok(views.html.index())
	}

	def list = Action { request =>

		val repo = new UserRepository()
		repo.setFilter(request.queryString("sSearch")(0))
		val json = repo.getDataTableJson(request.queryString, UserAdapter)

		Ok(json.toString)
	}

}

trait Adapter[Bean, Model] {
	def beanToModel(bean: Bean): Model
	def modelToColumns(model: Model): List[String]
	def columnIdToBeanName(columnId: Int): String
}

trait Repository[Bean, Model] {

	def listCriteria(session: org.hibernate.Session): org.hibernate.Criteria

	def size: Int = {
		val em = JPA.em("default")
		em.getTransaction().begin()

		val session = em.unwrap(classOf[org.hibernate.Session])
		val count: Long = this.listCriteria(session).setProjection(Projections.rowCount()).uniqueResult().asInstanceOf[Long]
		em.getTransaction().commit()

		count.toInt
	}

	def list(sortColumn: String = "", sortAsc: Boolean = true)(pageStart: Int = -1, pageSize: Int = -1): List[Bean] = {
		val em = JPA.em("default")
		em.getTransaction().begin()

		val session = em.unwrap(classOf[org.hibernate.Session])

		val criteria: org.hibernate.Criteria = this.listCriteria(session)
		if (pageStart >= 0 && pageSize >= 0) {
			criteria.setFirstResult(pageStart).setMaxResults(pageSize)
		}
		if (sortColumn != "") {
			if (sortAsc) {
				criteria.addOrder( Order.asc(sortColumn) )
			} else {
				criteria.addOrder( Order.desc(sortColumn) )
			}
		}


		val beans: List[Bean] = criteria.list.asScala.toList map (_.asInstanceOf[Bean])
		em.getTransaction().commit()

		beans
	}


	def getDataTableJson(queryString: Map[String, Seq[String]], adapter: Adapter[Bean, Model]
		): com.fasterxml.jackson.databind.node.ObjectNode = {

		// parameters
		val iTotalRecords: Int = this.size
		val filter: String = queryString("sSearch")(0)
		val pageSize: Int = queryString("iDisplayLength")(0).toInt
		val pageStart: Int = queryString("iDisplayStart")(0).toInt

		// sort
		val sortOrder: Boolean = queryString("sSortDir_0")(0) == "asc"
		val sortColumn: String = adapter.columnIdToBeanName( queryString("iSortCol_0")(0).toInt )

		// return Json
		val result = play.libs.Json.newObject()

		result.put("sEcho", queryString("sEcho")(0))
		result.put("iTotalRecords", iTotalRecords)
		result.put("iTotalDisplayRecords", iTotalRecords)

		val an: ArrayNode = result.putArray("aaData")

		this.list(sortColumn, sortOrder)(pageStart, pageSize) foreach { u => 
			val row: ArrayNode = new ArrayNode(JsonNodeFactory.instance)
			val values: List[String] = adapter.modelToColumns(adapter.beanToModel(u))
			values foreach { v => row.add(v)}
			an.add(row)
		}

		result
	}
}


class UserRepository extends Repository[beans.User, models.User] {

	private var filter = ""
	def setFilter(filter: String) = this.filter = filter

	override def listCriteria(session: org.hibernate.Session): org.hibernate.Criteria = {
		session.createCriteria(classOf[beans.User])
			.add(
				Restrictions.or(
						Restrictions.like(beans.User_.name, "%" + this.filter + "%"),
						Restrictions.like(beans.User_.phone, "%" + this.filter + "%")
					)
			)
	}

}

object UserAdapter extends Adapter[beans.User, models.User] {
	override def beanToModel(user: beans.User): models.User = {
		 models.User(user.getId, user.getName, user.getPhone)
	}
	override def modelToColumns(user: models.User): List[String] = {
		//List(user.id.toString, user.name, user.phone, "<button onclick=\"edit('"+user.id+"')\">edit</button> <button onclick=\"remove('"+user.id+"')\">delete</button>")
		List(user.id.toString, user.name, user.phone, "<button>edit</button> <button itemId="+user.id+" class='delete'>delete</button>")
	}
	override def columnIdToBeanName(columnId: Int): String = columnId match {
		case 0 => beans.User_.id
		case 1 => beans.User_.name
		case 2 => beans.User_.phone
	}
}