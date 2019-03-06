package com.mastercard.gateway.threeds2

class UiCustomization {

    enum class ButtonType { SUBMIT, CONTINUE, NEXT, CANCEL, RESEND }

    val buttonCustomizations = HashMap<String, ButtonCustomization>()
    var toolbarCustomization: ToolbarCustomization? = null
    var labelCustomization: LabelCustomization? = null
    var textBoxCustomization: TextBoxCustomization? = null

    fun getButtonCustomization(buttonType: ButtonType): ButtonCustomization? {
        return getButtonCustomization(buttonType.name)
    }

    fun getButtonCustomization(buttonType: String): ButtonCustomization? {
        return buttonCustomizations[buttonType]
    }

    fun setButtonCustomization(buttonCustomization: ButtonCustomization, buttonType: ButtonType) {
        setButtonCustomization(buttonCustomization, buttonType.name)
    }

    fun setButtonCustomization(buttonCustomization: ButtonCustomization, buttonType: String) {
        buttonCustomizations[buttonType] = buttonCustomization
    }


}