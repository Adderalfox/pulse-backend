package models

import java.time.LocalDateTime

case class NominationDraft(
                          id: String,
                          nomineeId: String,
                          requestedBy: String,
                          awardCategory: String,
                          draftText: String,
                          skillsCited: String,
                          appreciationsUsed: Int,
                          generatedAt: Option[LocalDateTime] = None
                          )