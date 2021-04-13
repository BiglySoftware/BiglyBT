#if __MAC_OS_X_VERSION_MAX_ALLOWED >= 1050
# include <objc/runtime.h>
# include <objc/message.h>
#else
# include <objc/objc-runtime.h>
#endif

#include "com_biglybt_platform_macosx_access_jnilib_OSXAccess.h"

// function aliases for the objc_msgSend prototype
// see https://www.mikeash.com/pyblog/objc_msgsends-new-prototype.html
// and https://indiestack.com/2019/10/casting-objective-c-message-sends/

id (*IdFromIdAndSelector)(id, SEL) = (id (*)(id, SEL)) objc_msgSend;
id (*IdFromClassAndSel)(Class, SEL) = (id (*)(Class, SEL)) objc_msgSend;
id (*IdFromSelIdChar)(id, SEL, char*) = (id (*)(id, SEL, char*)) objc_msgSend;
id (*IdFromIdSelLongId)(id, SEL, long, id) = (id (*)(id, SEL, long, id)) objc_msgSend;

static int osx_latencycritical_count = 0;
static id osx_latencycritical_activity = nil;

JNIEXPORT void JNICALL Java_com_biglybt_platform_macosx_access_jnilib_OSXAccess_disableAppNap
  (JNIEnv *jnienv, jclass c)
{
    Class pic;      /* Process info class */
    SEL pisl;       /* Process info selector */
    SEL bawo;       /* Begin Activity With Options selector */
    id pi;          /* Process info */
    id str;         /* Reason string */

    if (osx_latencycritical_count++ != 0)
        return;

    /* Avoid triggering an exception when run on older OS X */
    if ((pic = (Class)objc_getClass("NSProcessInfo")) == nil)
        return;

    if (class_getClassMethod(pic, (pisl = sel_getUid("processInfo"))) == NULL)
        return;

    if (class_getInstanceMethod(pic,
          (bawo = sel_getUid("beginActivityWithOptions:reason:"))) == NULL)
        return;

    /* Get the process instance */
    if ((pi = IdFromIdAndSelector((id)pic, pisl)) == nil)
        return;

    /* Create a reason string */
    str = IdFromClassAndSel(objc_getClass("NSString"), sel_getUid("alloc"));
    str = IdFromSelIdChar(str, sel_getUid("initWithUTF8String:"), "Timing Critical");

    /* Start activity that tells App Nap to mind its own business: */
    /* NSActivityUserInitiatedAllowingIdleSystemSleep */
    /* | NSActivityLatencyCritical */
    osx_latencycritical_activity = IdFromIdSelLongId(pi, bawo, 0x00FFFFFFULL | 0xFF00000000ULL, str);
}
