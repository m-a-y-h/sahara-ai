package pk.edu.ucp.saharaai.data.model

data class PaymentRequest(
    val requestId: String = "",
    val userId: String = "",
    val counselorKey: String = "",
    val counselorName: String = "",
    val amountPkr: String = "",
    val accountTitle: String = "",
    val transactionReference: String = "",
    val proofUrl: String = "",
    val status: String = "PENDING_REVIEW",
    val reviewedBy: String = "",
    val reviewNotes: String = "",
    val reviewAttachmentUrl: String = "",
    val createdAt: Long = 0L,
    val reviewedAt: Long = 0L
)
