package webserviceclients.acquire

import javax.inject.Inject
import play.api.Logger
import play.api.http.Status.OK
import uk.gov.dvla.vehicles.presentation.common.LogFormats
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class AcquireServiceImpl @Inject()(config: AcquireConfig, ws: AcquireWebService) extends AcquireService {

  override def invoke(cmd: AcquireRequestDto, trackingId: String): Future[(Int, Option[AcquireResponseDto])] = {
    val vrm = LogFormats.anonymize(cmd.registrationNumber)
    val refNo = LogFormats.anonymize(cmd.referenceNumber)
    val postcode = LogFormats.anonymize(cmd.traderDetails.traderPostCode)

    Logger.debug("Calling acquire vehicle micro-service with " +
      s"$refNo $vrm $postcode ${cmd.keeperConsent} ${cmd.keeperConsent} ${cmd.mileage}")

    ws.callDisposeService(cmd, trackingId).map { resp =>
      Logger.debug(s"Http response code from acquire vehicle micro-service was: ${resp.status}")

      if (resp.status == OK) (resp.status, resp.json.asOpt[AcquireResponseDto])
      else (resp.status, None)
    }
  }
}
