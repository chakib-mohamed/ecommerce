import React from "react";

interface TextareaInputProps {
  name: string;
  value: string;
  onChangeHandler: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  isValid?: boolean;
  className?: string;
  placeholder?: string;
  rows?: number;
}

const TextareaInput: React.FC<TextareaInputProps> = ({
  name,
  value,
  onChangeHandler,
  isValid = true,
  className = "",
  placeholder = "",
  rows = 4
}) => {
  return (
    <textarea
      name={name}
      value={value}
      onChange={onChangeHandler}
      placeholder={placeholder}
      rows={rows}
      className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 placeholder-slate-400 transition-all outline-none resize-none
        ${!isValid 
          ? 'border-red-100 focus:border-red-500 bg-red-50/10' 
          : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'} 
        ${className}`}
    />
  );
};

export default React.memo(TextareaInput);
