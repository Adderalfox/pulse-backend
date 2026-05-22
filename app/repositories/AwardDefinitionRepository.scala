package repositories

import models.AwardDefinition
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AwardDefinitionRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[slick.jdbc.JdbcProfile]

  import dbConfig._

  private class AwardDefinitionsTable(tag: Tag)
    extends Table[AwardDefinition](tag, "award_definitions") {

    def id = column[String]("id", O.PrimaryKey)

    def companyId = column[String]("company_id")

    def departmentId = column[Option[String]]("department_id")

    def name = column[String]("name")

    def description = column[String]("description")

    def criteriaText = column[String]("criteria_text")

    def createdBy = column[String]("created_by")

    def * = (id, companyId, departmentId, name, description, criteriaText, createdBy)
      .mapTo[AwardDefinition]
  }

  private val table = TableQuery[AwardDefinitionsTable]

  def insert(award: AwardDefinition): Future[AwardDefinition] =
    db.run(table += award).map(_ => award)

  def findById(id: String): Future[Option[AwardDefinition]] =
    db.run(table.filter(_.id === id).result.headOption)

  /** All awards visible to a given (company, department) pair.
   * Returns company-wide awards (department_id IS NULL) PLUS department-scoped awards. */
  def findByCompanyAndDepartment(
                                  companyId: String,
                                  departmentId: String
                                ): Future[Seq[AwardDefinition]] =
    db.run(
      table
        .filter(_.companyId === companyId)
        .filter(r => r.departmentId.isEmpty || r.departmentId === departmentId)
        .result
    )

  def findByCompany(companyId: String): Future[Seq[AwardDefinition]] =
    db.run(table.filter(_.companyId === companyId).result)

  def delete(id: String): Future[Int] =
    db.run(table.filter(_.id === id).delete)

  def update(award: AwardDefinition): Future[Int] =
    db.run(
      table
        .filter(_.id === award.id)
        .map(r => (r.name, r.description, r.criteriaText, r.departmentId))
        .update((award.name, award.description, award.criteriaText, award.departmentId))
    )
}