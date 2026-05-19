import React from "react";

interface SelectOption {
  value: string;
  name: string;
}

interface SelectProps {
  name: string;
  value: string;
  options: SelectOption[];
  onChangeHandler: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  isValid?: boolean;
  className?: string;
}

const Select: React.FC<SelectProps> = ({ 
  name, 
  value, 
  options, 
  onChangeHandler, 
  isValid = true, 
  className = "" 
}) => {
  return (
    <div className={`relative ${className}`}>
      <select
        name={name}
        value={value}
        onChange={onChangeHandler}
        className={`block w-full px-5 py-4 bg-white border-2 rounded-2xl text-slate-900 transition-all outline-none appearance-none cursor-pointer
          ${!isValid 
            ? 'border-red-100 focus:border-red-500 bg-red-50/10' 
            : 'border-transparent focus:border-blue-500 shadow-sm focus:shadow-blue-100'}`}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.name}
          </option>
        ))}
      </select>
      {/* Custom Chevron */}
      <div className="absolute inset-y-0 right-0 pr-4 flex items-center pointer-events-none text-slate-400">
        <i className="fa fa-chevron-down text-xs"></i>
      </div>
    </div>
  );
};

export default React.memo(Select);
