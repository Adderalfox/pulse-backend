package models.insights

case class UnderratedEmployee(
                             userId: String,
                             displayName: String,
                             department: String,
                             topSkills: List[String],
                             appreciationReceivedCount: Int,
                             workSignalScore: Double,
                             recognitionGapScore: Double
                             )

case class RecognitionImbalanceReport(
                                     teamId: String,
                                     teamName: String,
                                     giniCoefficient: Double,
                                     top20PercentShare: Double,
                                     highReceiversCount: Int,
                                     lowReceiversCount: Int,
                                     tag: String
                                     )

sealed trait TrendDirection
object TrendDirection {
  case object RISING extends TrendDirection
  case object STABLE extends TrendDirection
  case object FALLING extends TrendDirection
}

case class SkillTrend(
                     skillName: String,
                     category: String,
                     currentPeriodCount: Int,
                     previousPeriodCount: Int,
                     changePercent: Double,
                     trend: TrendDirection
                     )

case class ExpertResult(
                       userId: String,
                       displayName: String,
                       compositeScore: Double,
                       frequencyCount: Int,
                       appreciatorDiversity: Int,
                       explanation: String
                       )

case class ExpertQueryResult(
                            query: String,
                            extractedSkills: List[String],
                            experts: List[ExpertResult]
                            )