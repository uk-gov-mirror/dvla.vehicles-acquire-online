package controllers.acquire

import controllers.acquire.Common.PrototypeHtml
import controllers.BusinessKeeperDetails
import helpers.acquire.CookieFactoryForUnitSpecs
import helpers.UnitSpec
import uk.gov.dvla.vehicles.presentation.common.clientsidesession.ClientSideSessionFactory
import uk.gov.dvla.vehicles.presentation.common.mappings.OptionalToggle
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.BusinessNameId
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.EmailId
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.EmailOptionId
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.FleetNumberId
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.FleetNumberOptionId
import uk.gov.dvla.vehicles.presentation.common.model.BusinessKeeperDetailsFormModel.Form.PostcodeId
import org.mockito.Mockito.when
import pages.acquire.{NewKeeperChooseYourAddressPage, SetupTradeDetailsPage}
import pages.acquire.BusinessKeeperDetailsPage.{FleetNumberValid, BusinessNameValid, EmailValid, PostcodeValid}
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_REQUEST, LOCATION, OK, contentAsString, defaultAwaitTimeout}
import play.api.test.WithApplication
import utils.helpers.Config

class BusinessKeeperDetailsUnitSpec extends UnitSpec {
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

      val controller = new BusinessKeeperDetails()
      val result = controller.present(request)
      contentAsString(result) should not include PrototypeHtml
    }

    "display populated fields when cookie exists" in new WithApplication {
      val request = FakeRequest().
        withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel()).
        withCookies(CookieFactoryForUnitSpecs.businessKeeperDetailsModel())
      val result = businessKeeperDetails.present(request)
      val content = contentAsString(result)
      content should include(s"""value="$FleetNumberValid"""")
      content should include(s"""value="$BusinessNameValid"""")
      content should include(s"""value="$EmailValid"""")
      content should include(s"""value="$PostcodeValid"""")
    }

    "redirect to setup trade details when no cookie is present" in new WithApplication {
      val request = buildRequest()
      val result = businessKeeperDetails.present(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(SetupTradeDetailsPage.address))
      }
    }
  }

  "submit" should {
    "redirect to next page when only mandatory fields are filled in" in new WithApplication {
      val request = buildRequest(fleetNumber = None, email = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = businessKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(NewKeeperChooseYourAddressPage.address))
      }
    }

    "redirect to next page when all fields are complete" in new WithApplication {
      val request = buildRequest()
        .withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = businessKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(NewKeeperChooseYourAddressPage.address))
      }
    }

    "redirect to setup trade details when no cookie is present with invalid submission" in new WithApplication {
      val request = buildRequest(fleetNumber = Some("-12345"))
      val result = businessKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.headers.get(LOCATION) should equal(Some(SetupTradeDetailsPage.address))
      }
    }

    "return a bad request if no details are entered" in new WithApplication {
      val request = buildRequest(businessName = "", postcode = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = businessKeeperDetails.submit(request)
      whenReady(result) { r =>
        r.header.status should equal(BAD_REQUEST)
      }
    }

    "replace required error message for business name with standard error message " in new WithApplication {
      val request = buildRequest(businessName = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = businessKeeperDetails.submit(request)
      val errorMessage = "Must be between 2 and 30 characters and only contain valid characters"
      val count = errorMessage.r.findAllIn(contentAsString(result)).length
      count should equal(2)
    }

    "replace required error message for business postcode with standard error message " in new WithApplication {
      val request = buildRequest(postcode = "")
        .withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
      val result = businessKeeperDetails.submit(request)
      val errorMessage = "Must be between five and eight characters and in a valid format, e.g. AB1 2BA or AB12BA"
      val count = errorMessage.r.findAllIn(contentAsString(result)).length
      count should equal(2)
    }
  }

  private def buildRequest(fleetNumber: Option[String] = Some(FleetNumberValid),
                           businessName: String = BusinessNameValid,
                           email: String = EmailValid,
                           postcode: String = PostcodeValid) = {
    FakeRequest().withFormUrlEncodedBody(Seq(
      BusinessNameId -> businessName,
      EmailOptionId -> OptionalToggle.Invisible,
      EmailId -> email,
      PostcodeId -> postcode
    ) ++ fleetNumber.fold(Map(FleetNumberOptionId -> OptionalToggle.Invisible)) { fleetNumber =>
      Map(FleetNumberOptionId -> OptionalToggle.Visible, FleetNumberId -> fleetNumber)
    }:_*)
  }

  private lazy val businessKeeperDetails = {
    injector.getInstance(classOf[BusinessKeeperDetails])
  }

  private lazy val present = {
    val request = FakeRequest().withCookies(CookieFactoryForUnitSpecs.vehicleAndKeeperDetailsModel())
    businessKeeperDetails.present(request)
  }
}
