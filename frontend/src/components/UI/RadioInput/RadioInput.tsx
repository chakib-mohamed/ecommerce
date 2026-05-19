import React from "react";

interface RadioOption {
  name: string;
  value: string;
}

interface RadioInputProps {
  name: string;
  value: string;
  options: RadioOption[];
  onChangeHandler: (e: React.ChangeEvent<HTMLInputElement>) => void;
  isValid?: boolean;
  className?: string;
}

const RadioInput: React.FC<RadioInputProps> = ({ 
  name, 
  value, 
  options, 
  onChangeHandler,
  className = ""
}) => {
  return (
    <div className={`space-y-3 ${className}`}>
      {options &&
        options.map((option) => (
          <label key={option.value} className="flex items-center space-x-3 cursor-pointer group">
            <div className="relative flex items-center justify-center">
              <input
                id={`${name}-${option.value}`}
                name={name}
                type="radio"
                value={option.value}
                checked={value === option.value}
                onChange={onChangeHandler}
                className="peer appearance-none w-6 h-6 border-2 border-slate-200 rounded-full bg-white checked:border-blue-600 transition-all cursor-pointer shadow-sm group-hover:border-blue-400"
              />
              <div className="absolute w-2.5 h-2.5 bg-blue-600 rounded-full scale-0 peer-checked:scale-100 transition-transform pointer-events-none shadow-lg shadow-blue-500/50"></div>
            </div>
            <span className="text-sm font-bold text-slate-700 group-hover:text-blue-600 transition-colors tracking-tight italic">
              {option.name}
            </span>
          </label>
        ))}
    </div>
  );
};

export default React.memo(RadioInput);
