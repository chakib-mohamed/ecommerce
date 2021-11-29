import firebase from "firebase/app";
import "firebase/database";
import "firebase/auth";

const config = {
  apiKey: "AIzaSyBcYtAX_0OK5w3vrG_zh6fxf1nGQY-u92U",
  databaseURL: "https://ecommerce-41f9c.firebaseio.com/",
};

firebase.initializeApp(config);
export const db = firebase.database();
export const auth = firebase.auth();
