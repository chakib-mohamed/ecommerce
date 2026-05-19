import React from "react";

interface TextInputProps {
  name: string;
  value: string | number;
  onChangeHandler: (e: React.ChangeEvent<HTMLInputElement>) => void;
  isValid?: boolean;
  className?: string;
  placeholder?: string;
  type?: string;
}

const TextInput: React.FC<TextInputProps> = ({ 
  name, 
  value, 
  onChangeHandler, 
  isValid = true, 
  className = "", 
  placeholder = "",
  type = "text"
}) => {
  return (
    <input
      type={type}
      name={name}
      value={value}
      onChange={onChangeHandler}
      placeholder={placeholder}
      className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none
        ${!isValid 
          ? 'border-red-100 focus:border-red-500 bg-red-50/10' 
          : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'} 
        ${className}`}
    />
  );
};

export default React.memo(TextInput);
