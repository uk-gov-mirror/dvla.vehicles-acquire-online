package controllers

import com.google.inject.Inject
import controllers.routes.VehicleLookup
import play.api.mvc.{Action, Controller}
import uk.gov.dvla.vehicles.presentation.common
import common.clientsidesession.ClientSideSessionFactory
import common.clientsidesession.CookieImplicits.{RichCookies, RichResult}
import utils.helpers.Config

final class MicroServiceError @Inject()(implicit clientSideSessionFactory: ClientSideSessionFactory,
                                        config: Config) extends Controller {
  private final val DefaultRedirectUrl = VehicleLookup.present().url

  def present = Action { implicit request =>
    val referer = request.headers.get(REFERER).getOrElse(DefaultRedirectUrl)
    Ok(views.html.acquire.micro_service_error()).
      // Save the previous page URL (from the referer header) into a cookie.
      withCookie(MicroServiceError.MicroServiceErrorRefererCacheKey, referer)
  }

  def back = Action { implicit request =>
    val referer: String = request.cookies.getString(MicroServiceError.MicroServiceErrorRefererCacheKey).getOrElse(DefaultRedirectUrl)
    Redirect(referer).discardingCookie(MicroServiceError.MicroServiceErrorRefererCacheKey)
  }
}

object MicroServiceError {
  final val MicroServiceErrorRefererCacheKey = "msError"
}