import React, { useEffect } from "react";
import { Redirect } from "react-router-dom";
import { toast } from "react-toastify";

export const SessionTimeout = () => {
  useEffect(() => {
    toast.warn("You've been inactive for a long time, please login again");
  }, []);

  return <Redirect to="/login"></Redirect>;
};
