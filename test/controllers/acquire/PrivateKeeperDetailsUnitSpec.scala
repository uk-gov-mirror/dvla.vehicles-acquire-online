package controllers.acquire

import play.api.test.WithApplication
import controllers.acquire.Common.PrototypeHtml
import helpers.acquire.CookieFactoryForUnitSpecs
import controllers.PrivateKeeperDetails
import helpers.UnitSpec
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers.{LOCATION, BAD_REQUEST, OK, contentAsString, defaultAwaitTimeout}
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.ClientSideSessionFactory
import utils.helpers.Config
import models.PrivateKeeperDetailsFormModel.Form.{TitleId, EmailId, FirstNameId, LastNameId}
import pages.acquire.PrivateKeeperDetailsPage.{TitleValid, FirstNameValid, EmailValid, TitleInvalidError, LastNameValid}
import pages.acquire.SetupTradeDetailsPage

class PrivateKeeperDetailsUnitSpec extends UnitSpec {

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

    "display populated fields when cookie exists" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsModel())
      val result = privateKeeperDetails.present(request)
      val content = contentAsString(result)
      content should include(TitleValid)
      content should include(FirstNameValid)
      content should include(LastNameValid)
      content should include(EmailValid)
    }

    "display empty fields when cookie does not exist" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.privateKeeperDetailsModel())
      val result = privateKeeperDetails.present(request)
      val content = contentAsString(result)
      content should include(TitleValid)
      content should not include "selected"
    }
  }

  "submit" should {
    "redirect to next page when mandatory fields are complete" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(email = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
      val result = privateKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some("/vrm-acquire/private-complete-and-confirm")) //ToDo amend when next page implemented
      }
    }

    "redirect to next page when all fields are complete" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest()
        .withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
      val result = privateKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some("/vrm-acquire/private-complete-and-confirm")) //ToDo amend when next page implemented
      }
    }

    "redirect to setup trade details when no cookie is present" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(title = TitleValid)
      val result = privateKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(SetupTradeDetailsPage.address))
      }
    }

    "return a bad request if no details are entered" in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(title = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
      val result = privateKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.status should equal(BAD_REQUEST)
      }
    }

    "replace required error message for title with standard error message " in new WithApplication {
      val request = buildCorrectlyPopulatedRequest(title = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
      val result = privateKeeperDetails.submit(request)
      val count = TitleInvalidError.
        r.findAllIn(contentAsString(result)).length
      count should equal(2)
    }
  }

  private def buildCorrectlyPopulatedRequest(title: String = TitleValid,
                                             firstName: String = FirstNameValid,
                                             lastName: String = LastNameValid,
                                             email: String = EmailValid) = {
    FakeRequest().withFormUrlEncodedBody(
      TitleId -> title,
      FirstNameId -> firstName,
      LastNameId -> lastName,
      EmailId -> email
    )
  }

  private val privateKeeperDetails = {
    injector.getInstance(classOf[PrivateKeeperDetails])
  }

  private lazy val present = {
    val request = FakeRequest().
      withCookies(CookieFactoryForUnitSpecs.vehicleDetailsModel())
    privateKeeperDetails.present(request)
  }
}
