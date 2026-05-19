import React, { useEffect } from "react";
import { Navigate } from "react-router-dom";
import { toast } from "react-toastify";

const SessionTimeout: React.FC = () => {
  useEffect(() => {
    toast.warn("You've been inactive for a long time, please login again");
  }, []);

  return <Navigate to="/login" replace />;
};

export default SessionTimeout;
