package app.vibefinancier;

import org.json.JSONObject;

/** Resolves icon gestures without creating or modifying any financial records. */
public final class DragOperation {
    private DragOperation(){}
    public static boolean canStart(JSONObject entity){return entity!=null&&!entity.optString("id").isEmpty()&&!entity.optBoolean("archived")&&!entity.optBoolean("deleted")&&(entity.optString("entity").equals("account")||entity.optString("entity").equals("category")&&entity.optString("direction").equals("income"));}
    public static JSONObject resolve(JSONObject from,JSONObject to){
        if(!canStart(from)||to==null||to.optBoolean("archived")||to.optBoolean("deleted")||from.optString("id").equals(to.optString("id")))return null;
        boolean a=from.optString("entity").equals("account"),b=to.optString("entity").equals("account");
        if(a&&b)return Ledger.obj("kind","transfer","account",from.optString("id"),"to",to.optString("id"),"category","");
        if(a==b)return null;
        JSONObject account=a?from:to,category=a?to:from;String direction=category.optString("direction");
        if(account.optString("account_type").equals("debt")||(!direction.equals("income")&&!direction.equals("expense")))return null;
        return Ledger.obj("kind",direction,"account",account.optString("id"),"category",category.optString("id"),"to","");
    }
}
