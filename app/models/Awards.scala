package models

import play.api.libs.json.{Json, OFormat, OWrites, Reads}

case class AwardDefinition(
                            id:           String,
                            companyId:    String,
                            departmentId: Option[String],
                            name:         String,
                            description:  String,
                            criteriaText: String,
                            createdBy:    String
                          )

object AwardDefinition {
  implicit val format: OFormat[AwardDefinition] = Json.format[AwardDefinition]
}

case class AwardRecommendation(
                                id:                String,
                                nomineeId:         String,
                                requestedBy:       String,
                                recommendedAwards: String,
                                profileSummary:    String
                              )

object AwardRecommendation {
  implicit val format: OFormat[AwardRecommendation] = Json.format[AwardRecommendation]
}

case class RankedAward(
                        awardId:      String,
                        awardName:    String,
                        score:        Float,
                        criteriaText: String
                      )

object RankedAward {
  implicit val format: OFormat[RankedAward] = Json.format[RankedAward]
}

case class RecommendationResult(
                                 nomineeId:      String,
                                 profileSummary: String,
                                 rankedAwards:   List[RankedAward]
                               )

object RecommendationResult {
  implicit val writes: OWrites[RecommendationResult] = Json.writes[RecommendationResult]
}