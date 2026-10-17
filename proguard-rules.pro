-dontshrink
-dontoptimize

-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,NestHost,NestMembers,Record,PermittedSubclasses

-keep class net.example.mod.ExampleMod {
    public <init>();
    public void onInitializeClient();
}

-keep,includedescriptorclasses class net.example.mod.mixin.** {
    *;
}

-keep,includedescriptorclasses class com.example.mixin.** {
    *;
}

-keepclassmembers class * {
    public <methods>;
    protected <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-printmapping build/obfuscated/mapping.txt
