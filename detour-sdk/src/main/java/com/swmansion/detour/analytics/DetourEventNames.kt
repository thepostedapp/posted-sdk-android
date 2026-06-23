package com.swmansion.detour.analytics

/**
 * Predefined analytics event names matching the React Native SDK.
 *
 * @property eventName The wire-format name sent to the analytics API.
 */
enum class DetourEventNames(val eventName: String) {
    Login("login"),
    Search("search"),
    Share("share"),
    SignUp("sign_up"),
    TutorialBegin("tutorial_begin"),
    TutorialComplete("tutorial_complete"),
    ReEngage("re_engage"),
    Invite("invite"),
    OpenedFromPushNotification("opened_from_push_notification"),
    AddPaymentInfo("add_payment_info"),
    AddShippingInfo("add_shipping_info"),
    AddToCart("add_to_cart"),
    RemoveFromCart("remove_from_cart"),
    Refund("refund"),
    ViewItem("view_item"),
    BeginCheckout("begin_checkout"),
    Purchase("purchase"),
    AdImpression("ad_impression"),
    OpenedViaUniversalLink("opened_via_universal_link"),
}
