package views.acquire

import helpers.UiSpec
import helpers.common.ProgressBar.progressStep
import helpers.acquire.CookieFactoryForUISpecs
import helpers.tags.UiTag
import helpers.webbrowser.TestHarness
import org.openqa.selenium.{By, WebDriver, WebElement}
import pages.common.ErrorPanel
import pages.acquire.NewKeeperEnterAddressManuallyPage.{happyPath, happyPathMandatoryFieldsOnly, sadPath}
import pages.acquire.{BeforeYouStartPage, NewKeeperEnterAddressManuallyPage, VehicleTaxOrSornPage}
import uk.gov.dvla.vehicles.presentation.common.filters.CsrfPreventionAction
import pages.common.Feedback.AcquireEmailFeedbackLink

final class NewKeeperEnterAddressManuallyIntegrationSpec extends UiSpec with TestHarness {
  "go to page" should {
    "display the page" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      go to NewKeeperEnterAddressManuallyPage
      page.title should equal(NewKeeperEnterAddressManuallyPage.title)
    }

    "contain feedback email facility with appropriate subject" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      go to NewKeeperEnterAddressManuallyPage
      page.source.contains(AcquireEmailFeedbackLink) should equal(true)
    }

    "display the progress of the page when progressBar is set to true" taggedAs UiTag in new ProgressBarTrue {
      go to BeforeYouStartPage
      cacheSetup()
      go to NewKeeperEnterAddressManuallyPage
      page.source.contains(progressStep(6)) should equal(true)
    }

    "not display the progress of the page when progressBar is set to false" taggedAs UiTag in new ProgressBarFalse {
      go to BeforeYouStartPage
      cacheSetup()
      go to NewKeeperEnterAddressManuallyPage
      page.source.contains(progressStep(6)) should equal(false)
    }

    "contain the hidden csrfToken field" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      go to NewKeeperEnterAddressManuallyPage
      val csrf: WebElement = webDriver.findElement(By.name(CsrfPreventionAction.TokenName))
      csrf.getAttribute("type") should equal("hidden")
      csrf.getAttribute("name") should equal(uk.gov.dvla.vehicles.presentation.common.filters.CsrfPreventionAction.TokenName)
      csrf.getAttribute("value").size > 0 should equal(true)
    }
  }

  "next button" should {
    "accept and redirect when all fields are input with valid entry" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      happyPath()
      page.title should equal(VehicleTaxOrSornPage.title)
    }

    "accept when only mandatory fields only are input" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      happyPathMandatoryFieldsOnly()
      page.title should equal(VehicleTaxOrSornPage.title)
    }

    "display validation error messages when no details are entered" taggedAs UiTag in new WebBrowser {
      go to BeforeYouStartPage
      cacheSetup()
      sadPath
      ErrorPanel.numberOfErrors should equal(2)
    }
  }

  private def cacheSetup()(implicit webDriver: WebDriver) =
    CookieFactoryForUISpecs
      .vehicleDetails()
      .businessKeeperDetails() // Not bothering with private keeper details
}