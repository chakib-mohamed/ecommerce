import React from "react";

interface ValidationMessagesProps {
  element: {
    validators?: {
      [key: string]: {
        isValid: boolean;
        message: string;
      };
    };
    isTouched?: boolean;
  };
}

const ValidationMessages: React.FC<ValidationMessagesProps> = ({ element }) => {
  if (!element.isTouched || !element.validators) return null;

  return (
    <div className="mt-2 space-y-1 ml-1 animate-in fade-in slide-in-from-top duration-300">
      {Object.keys(element.validators).map((v) => {
        const validator = element.validators![v];
        if (!validator.isValid) {
          return (
            <div key={v} className="flex items-center space-x-2 text-red-500 animate-in fade-in duration-300">
              <i className="fa fa-exclamation-circle text-[10px]"></i>
              <span className="text-[10px] font-black uppercase tracking-tight italic">
                {validator.message}
              </span>
            </div>
          );
        }
        return null;
      })}
    </div>
  );
};

export default React.memo(ValidationMessages);
