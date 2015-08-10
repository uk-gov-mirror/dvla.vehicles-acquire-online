package controllers

import com.google.inject.Inject
import play.api.Logger
import play.api.mvc.{Action, Controller}
import uk.gov.dvla.vehicles.presentation.common
import uk.gov.dvla.vehicles.presentation.common.LogFormats.{DVLALogger}
import common.clientsidesession.ClientSideSessionFactory
import common.clientsidesession.CookieImplicits.RichCookies
import utils.helpers.Config

class UprnNotFound @Inject()()(implicit clientSideSessionFactory: ClientSideSessionFactory,
                                     config: Config)  extends Controller with DVLALogger {

  def present = Action { implicit request =>
    logMessage(request.cookies.trackingId(),Warn,s"Uprn not found")
    Ok(views.html.common.uprn_not_found())
  }
}