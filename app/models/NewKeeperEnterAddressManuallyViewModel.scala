package models

import play.api.data.Form
import uk.gov.dvla.vehicles.presentation.common
import common.model.VehicleDetailsModel

case class NewKeeperEnterAddressManuallyViewModel(form: Form[models.NewKeeperEnterAddressManuallyFormModel],
                                                  vehicleDetails: VehicleDetailsModel)