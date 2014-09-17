package controllers.acquire

import controllers.{PrivateKeeperDetails, PrivateKeeperDetailsComplete}
import helpers.UnitSpec
import helpers.acquire.CookieFactoryForUnitSpecs
import play.api.test.Helpers._
import play.api.test.{FakeRequest, WithApplication}
import controllers.acquire.Common._
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.ClientSideSessionFactory
import pages.acquire.PrivateKeeperDetailsCompletePage._
import utils.helpers.Config
import org.mockito.Mockito._
import pages.acquire.SetupTradeDetailsPage
import models.PrivateKeeperDetailsCompleteFormModel.Form._
import uk.gov.dvla.vehicles.presentation.common.mappings.DayMonthYear._
import scala.Some

class PrivateKeeperDetailsCompleteUnitSpec extends UnitSpec {

  "present" should {
    "display the page" in new WithApplication {
      whenReady(present) { r =>
        r.header.status should equal(OK)
      }
    }

    "display prototype message when config set to true" in new WithApplication {
      contentAsString(present) should include(PrototypeHtml)
    }

    "not display prototype message when config set to false" in new WithApplication {
      val request = FakeRequest()
      implicit val clientSideSessionFactory = injector.getInstance(classOf[ClientSideSessionFactory])
      implicit val config: Config = mock[Config]
      when(config.isPrototypeBannerVisible).thenReturn(false)

      val privateKeeperDetailsPrototypeNotVisible = new PrivateKeeperDetails()
      val result = privateKeeperDetailsPrototypeNotVisible.present(request)
      contentAsString(result) should not include PrototypeHtml
    }

    "present a full form when privateKeeperDetailsComplete cookie is present" in new WithApplication {
      val request = FakeRequest()
        .withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsModel())
        .withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsCompleteModel())
      val content = contentAsString(privateKeeperDetailsComplete.present(request))
      content should include(DayDateOfBirthValid)
      content should include(MonthDateOfBirthValid)
      content should include(YearDateOfBirthValid)
      content should include(MileageValid)
    }

    "display empty fields when privateKeeperDetailsComplete cookie does not exist" in new WithApplication {
      val request = FakeRequest()
      val result = privateKeeperDetailsComplete.present(request)
      val content = contentAsString(result)
      content should not include YearDateOfBirthValid
      content should not include MileageValid
    }

    "redirect to setuptrade details when no privatekeeperdetails cookie is present" in new WithApplication {
      val request = FakeRequest()
      val result = privateKeeperDetailsComplete.present(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(SetupTradeDetailsPage.address))
      }
    }
  }

  "submit" should {
    "redirect to next page when mandatory fields are complete" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest()

      val result = privateKeeperDetailsComplete.submit(request)
      whenReady(result) {
        r =>
          r.header.headers.get(LOCATION) should equal (Some("/vrm-acquire/not-implemented")) //ToDo - update when next section is implemented
      }
    }

    "redirect to next page when all fields are complete" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest()

      val result = privateKeeperDetailsComplete.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal (Some("/vrm-acquire/not-implemented")) //ToDo - update when next section is implemented
      }
    }

//    "not redirect when mandadtory fields are not completed" in new WithApplication {
//      val request = buildCorrectlyPopulatedRequest(consent = "")
//
//      val result = privateKeeperDetailsComplete.submit(request)
//      whenReady(result) { r =>
//        r.header.headers.get(LOCATION) should equal (Some(PrivateKeeperDetailsCompletePage.address)) //ToDo - update when consent is implemented
//      }
//    }

//    "return a bad request if no details are entered" in new WithApplication { // ToDo - uncomment when consent box is implemented
//      val request = buildCorrectlyPopulatedRequest()
//        .withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
//      val result = privateKeeperDetailsComplete.submit(request)
//      whenReady(result) { r =>
//        r.header.status should equal(BAD_REQUEST)
//      }
//    }
  }

  private def buildCorrectlyPopulatedRequest(dayDateOfBirth: String = DayDateOfBirthValid,
                                             monthDateOfBirth: String = MonthDateOfBirthValid,
                                             yearDateOfBirth: String = YearDateOfBirthValid,
                                             mileage: String = MileageValid) = {
    FakeRequest().withFormUrlEncodedBody(
      s"$DateOfBirthId.$DayId" -> dayDateOfBirth,
      s"$DateOfBirthId.$MonthId" -> monthDateOfBirth,
      s"$DateOfBirthId.$YearId" -> yearDateOfBirth,
      MileageId -> mileage
    )
  }

  private val privateKeeperDetailsComplete = {
    injector.getInstance(classOf[PrivateKeeperDetailsComplete])
  }

  private lazy val present = {
    val request = FakeRequest().
      withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsModel())
    privateKeeperDetailsComplete.present(request)
  }
}
